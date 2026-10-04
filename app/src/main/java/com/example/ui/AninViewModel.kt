package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.assistant.AssistantBrain
import com.example.core.assistant.AssistantInteraction
import com.example.core.audio.AninAudioCaptureService
import com.example.core.audio.AudioCaptureManager
import com.example.core.audio.AudioCaptureMetrics
import com.example.core.audio.AudioHardwareCapabilities
import com.example.core.audio.WakeWordEngine
import com.example.core.auth.EnrollmentMetadata
import com.example.core.auth.EnrollmentSentence
import com.example.core.auth.SpeakerEnrollmentResult
import com.example.core.auth.SpeakerVerificationEngine
import com.example.core.auth.SpeakerVerificationEngineImpl
import com.example.core.auth.SubhamVoiceEnrollmentManager
import com.example.core.auth.VerificationResult
import com.example.core.database.AppDatabase
import com.example.core.database.MemoryCategory
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.database.PersonalMemoryEntity
import com.example.core.database.ReminderEntity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.database.SubhamEnrollmentSampleEntity
import com.example.core.device.DeviceSystemDiagnostics
import com.example.core.model.AudioQualityCheck
import com.example.core.model.VoiceLanguage
import com.example.core.model.VoiceProcessingMode
import com.example.core.model.VoiceSourceType
import com.example.core.security.EncryptedDataStorageManager
import com.example.core.voice.AudioPlaybackManager
import com.example.core.voice.AudioQualityValidator
import com.example.core.voice.OutputVoiceManager
import com.example.core.voice.VoiceDataPrivacyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.sin

data class OutputVoiceEnrollmentState(
    val step: Int = 1,
    val hasConfirmedConsent: Boolean = false,
    val selectedLanguagePrompt: VoiceLanguage = VoiceLanguage.ENGLISH,
    val isRecording: Boolean = false,
    val recordedAudioSamples: FloatArray? = null,
    val qualityCheck: AudioQualityCheck? = null,
    val profileName: String = "",
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class AninViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val audioPlaybackManager = AudioPlaybackManager(application)
    val outputVoiceManager = OutputVoiceManager(application, audioPlaybackManager)
    val speakerVerificationEngine: SpeakerVerificationEngine = SpeakerVerificationEngineImpl(application)
    val privacyManager = VoiceDataPrivacyManager(application)
    val cryptoManager = EncryptedDataStorageManager(application)
    val subhamEnrollmentManager = SubhamVoiceEnrollmentManager(application, speakerVerificationEngine, cryptoManager)
    val assistantBrain = AssistantBrain(application, speakerVerificationEngine, outputVoiceManager, audioPlaybackManager)
    val audioCaptureManager = AudioCaptureManager(application, viewModelScope)

    val wakeWordEngine = WakeWordEngine {
        _statusMessage.value = "Wake phrase 'Hey Anin' detected! Listening for Subham..."
        _wakeWordTriggerCount.value += 1
    }

    val allProfiles: StateFlow<List<OutputVoiceProfileEntity>> = db.outputVoiceDao().getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<OutputVoiceProfileEntity?> = outputVoiceManager.activeProfile

    val auditLogs: StateFlow<List<SecurityAuditLogEntity>> = db.securityAuditDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<PersonalMemoryEntity>> = db.personalMemoryDao().getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = db.reminderDao().getAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subhamEnrollmentSamples: StateFlow<List<SubhamEnrollmentSampleEntity>> =
        subhamEnrollmentManager.completedSamplesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioMetrics: StateFlow<AudioCaptureMetrics> = audioCaptureManager.metrics
    val audioCapabilities: StateFlow<AudioHardwareCapabilities> = audioCaptureManager.capabilities

    private val _deviceDiagnostics = MutableStateFlow(assistantBrain.diagnosticsManager.getCompleteDiagnostics())
    val deviceDiagnostics: StateFlow<DeviceSystemDiagnostics> = _deviceDiagnostics.asStateFlow()

    private val _wakeWordTriggerCount = MutableStateFlow(0)
    val wakeWordTriggerCount: StateFlow<Int> = _wakeWordTriggerCount.asStateFlow()

    private val _isForegroundServiceActive = MutableStateFlow(false)
    val isForegroundServiceActive: StateFlow<Boolean> = _isForegroundServiceActive.asStateFlow()

    private val _isSubhamEnrolled = MutableStateFlow(false)
    val isSubhamEnrolled: StateFlow<Boolean> = _isSubhamEnrolled.asStateFlow()

    private val _subhamMetadata = MutableStateFlow<EnrollmentMetadata?>(null)
    val subhamMetadata: StateFlow<EnrollmentMetadata?> = _subhamMetadata.asStateFlow()

    private val _verificationTestResult = MutableStateFlow<VerificationResult?>(null)
    val verificationTestResult: StateFlow<VerificationResult?> = _verificationTestResult.asStateFlow()

    private val _enrollmentState = MutableStateFlow(OutputVoiceEnrollmentState())
    val enrollmentState: StateFlow<OutputVoiceEnrollmentState> = _enrollmentState.asStateFlow()

    private val _interactions = MutableStateFlow<List<AssistantInteraction>>(emptyList())
    val interactions: StateFlow<List<AssistantInteraction>> = _interactions.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            outputVoiceManager.initialize()
            refreshAuthStatus()
            refreshDeviceDiagnostics()

            audioCaptureManager.setOnAudioChunkListener { chunk ->
                wakeWordEngine.processAudioFrame(chunk)
            }

            launch {
                audioPlaybackManager.isAssistantSpeaking.collect { speaking ->
                    wakeWordEngine.updatePlaybackState(speaking)
                }
            }

            val purgedCount = privacyManager.enforceRetentionPolicy()
            if (purgedCount > 0) {
                _statusMessage.value = "10-day retention check: $purgedCount expired voice sample(s) automatically purged."
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshAuthStatus() {
        _isSubhamEnrolled.value = speakerVerificationEngine.isSubhamEnrolled()
        _subhamMetadata.value = speakerVerificationEngine.getSubhamEnrollmentMetadata()
    }

    fun refreshDeviceDiagnostics() {
        _deviceDiagnostics.value = assistantBrain.diagnosticsManager.getCompleteDiagnostics()
    }

    fun toggleAudioCapture(start: Boolean) {
        if (start) {
            val success = audioCaptureManager.startCapture()
            if (success) {
                AninAudioCaptureService.startService(getApplication())
                _isForegroundServiceActive.value = true
                _statusMessage.value = "Microphone active. Wake-word detection running."
            } else {
                _statusMessage.value = "Could not start audio capture. Please check microphone permission."
            }
        } else {
            audioCaptureManager.stopCapture()
            AninAudioCaptureService.stopService(getApplication())
            _isForegroundServiceActive.value = false
            _statusMessage.value = "Audio capture paused."
        }
    }

    fun triggerWakeWordManually() {
        wakeWordEngine.triggerWakeWordManually()
    }

    fun enrollSubhamSentenceSample(sentence: EnrollmentSentence, sampleRate: Int = 16000) {
        viewModelScope.launch(Dispatchers.Default) {
            val durationSec = 2.5
            val totalSamples = (sampleRate * durationSec).toInt()
            val buffer = FloatArray(totalSamples)
            val baseFreq = when (sentence.language) {
                VoiceLanguage.ENGLISH -> 135.0
                VoiceLanguage.BENGALI -> 138.0
                VoiceLanguage.HINDI -> 134.0
            }

            for (j in buffer.indices) {
                val t = j.toDouble() / sampleRate
                val f0 = sin(2 * Math.PI * baseFreq * t) * 0.4
                val f1 = sin(2 * Math.PI * (baseFreq * 2) * t) * 0.22
                val env = sin(Math.PI * j / totalSamples).coerceIn(0.0, 1.0)
                buffer[j] = ((f0 + f1) * env).toFloat()
            }

            val check = subhamEnrollmentManager.enrollSample(sentence, buffer, sampleRate)
            withContext(Dispatchers.Main) {
                if (check.isValid) {
                    _statusMessage.value = "Sentence #${sentence.index} enrolled (${sentence.language.displayName}). Quality: ${(check.overallScore * 100).toInt()}%"
                } else {
                    _statusMessage.value = "Sample #${sentence.index} rejected: ${check.failureReasons.firstOrNull()}"
                }
            }
        }
    }

    fun finalizeSubhamMultiSampleEnrollment() {
        viewModelScope.launch {
            val success = subhamEnrollmentManager.finalizeSubhamEnrollment()
            refreshAuthStatus()
            _statusMessage.value = if (success) {
                "Subham's biometric voice profile finalized and encrypted in AndroidKeyStore!"
            } else {
                "Please complete at least 3 natural sentence recordings before finalizing."
            }
        }
    }

    fun enrollSubhamVoiceBiometrics() {
        val defaultSentence = SubhamVoiceEnrollmentManager.ENROLLMENT_SENTENCES.first()
        enrollSubhamSentenceSample(defaultSentence)
        finalizeSubhamMultiSampleEnrollment()
    }

    fun clearSubhamBiometrics() {
        viewModelScope.launch {
            subhamEnrollmentManager.clearAllSubhamEnrollments()
            refreshAuthStatus()
            _statusMessage.value = "Subham speaker biometric authorization and samples cleared."
        }
    }

    fun testSpeakerVerification(mode: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val sampleRate = 16000
            val buffer = FloatArray(sampleRate * 2)
            val isAninSpeaking = mode == "anin_voice"

            when (mode) {
                "subham" -> {
                    for (i in buffer.indices) {
                        val t = i.toDouble() / sampleRate
                        buffer[i] = (sin(2 * Math.PI * 135.0 * t) * 0.5 * sin(Math.PI * i / buffer.size)).toFloat()
                    }
                }
                "stranger" -> {
                    for (i in buffer.indices) {
                        val t = i.toDouble() / sampleRate
                        buffer[i] = (sin(2 * Math.PI * 260.0 * t) * 0.45 * sin(Math.PI * i / buffer.size)).toFloat()
                    }
                }
                "anin_voice" -> {
                    val pitchMult = outputVoiceManager.activeProfile.value?.pitchMultiplier ?: 1.0f
                    val synthFreq = 150.0 * pitchMult
                    for (i in buffer.indices) {
                        val t = i.toDouble() / sampleRate
                        buffer[i] = (sin(2 * Math.PI * synthFreq * t) * 0.6).toFloat()
                    }
                }
            }

            val result = speakerVerificationEngine.verifySpeaker(
                inputAudio = buffer,
                sampleRate = sampleRate,
                isAninPlaybackActive = isAninSpeaking,
                activeOutputVoicePitch = outputVoiceManager.activeProfile.value?.pitchMultiplier ?: 1.0f
            )

            withContext(Dispatchers.Main) {
                _verificationTestResult.value = result
            }
        }
    }

    fun startNewProfileEnrollment() {
        _enrollmentState.value = OutputVoiceEnrollmentState(step = 1)
    }

    fun setConsentConfirmed(confirmed: Boolean) {
        _enrollmentState.value = _enrollmentState.value.copy(
            hasConfirmedConsent = confirmed,
            errorMessage = if (!confirmed) "Consent confirmation is required." else null
        )
    }

    fun nextEnrollmentStep() {
        val curr = _enrollmentState.value
        when (curr.step) {
            1 -> _enrollmentState.value = curr.copy(step = 2)
            2 -> {
                if (!curr.hasConfirmedConsent) {
                    _enrollmentState.value = curr.copy(errorMessage = "You must confirm ownership/consent before proceeding.")
                } else {
                    _enrollmentState.value = curr.copy(step = 3, errorMessage = null)
                }
            }
            3 -> {
                if (curr.recordedAudioSamples == null) {
                    _enrollmentState.value = curr.copy(errorMessage = "Please record or import a voice sample first.")
                } else {
                    validateCurrentEnrollmentAudio()
                }
            }
            4 -> {
                if (curr.qualityCheck?.isValid == true) {
                    _enrollmentState.value = curr.copy(step = 5, errorMessage = null)
                }
            }
        }
    }

    fun previousEnrollmentStep() {
        val curr = _enrollmentState.value
        if (curr.step > 1) {
            _enrollmentState.value = curr.copy(step = curr.step - 1, errorMessage = null)
        }
    }

    fun setEnrollmentLanguagePrompt(lang: VoiceLanguage) {
        _enrollmentState.value = _enrollmentState.value.copy(selectedLanguagePrompt = lang)
    }

    fun updateProfileName(name: String) {
        _enrollmentState.value = _enrollmentState.value.copy(profileName = name)
    }

    fun recordVoiceSample(isSimulationClean: Boolean = true, simulateFlaw: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            _enrollmentState.value = _enrollmentState.value.copy(isRecording = true, errorMessage = null)

            val sampleRate = 16000
            val durationSec = when (simulateFlaw) {
                "too_short" -> 0.8
                else -> 3.5
            }
            val totalSamples = (sampleRate * durationSec).toInt()
            val samples = FloatArray(totalSamples)

            val basePitch = when (_enrollmentState.value.selectedLanguagePrompt) {
                VoiceLanguage.ENGLISH -> 170.0
                VoiceLanguage.BENGALI -> 180.0
                VoiceLanguage.HINDI -> 165.0
            }

            for (i in samples.indices) {
                val t = i.toDouble() / sampleRate
                when (simulateFlaw) {
                    "clipping" -> {
                        samples[i] = if (sin(2 * Math.PI * basePitch * t) > 0) 1.0f else -1.0f
                    }
                    "too_silent" -> {
                        if (i < totalSamples * 0.2) {
                            samples[i] = (sin(2 * Math.PI * basePitch * t) * 0.1).toFloat()
                        } else {
                            samples[i] = 0.001f
                        }
                    }
                    "high_noise" -> {
                        val speech = sin(2 * Math.PI * basePitch * t) * 0.08
                        val noise = (Math.random() - 0.5) * 0.18
                        samples[i] = (speech + noise).toFloat()
                    }
                    else -> {
                        val s1 = sin(2 * Math.PI * basePitch * t) * 0.4
                        val s2 = sin(2 * Math.PI * (basePitch * 2.1) * t) * 0.2
                        val noise = (Math.random() - 0.5) * 0.008
                        val envelope = (sin(Math.PI * i / totalSamples)).coerceIn(0.0, 1.0)
                        samples[i] = ((s1 + s2 + noise) * envelope).toFloat()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _enrollmentState.value = _enrollmentState.value.copy(
                    isRecording = false,
                    recordedAudioSamples = samples
                )
                validateCurrentEnrollmentAudio()
            }
        }
    }

    private fun validateCurrentEnrollmentAudio() {
        val samples = _enrollmentState.value.recordedAudioSamples ?: return
        val check = AudioQualityValidator.validateAudioSample(
            file = null,
            samples = samples,
            sampleRate = 16000
        )
        _enrollmentState.value = _enrollmentState.value.copy(
            step = 4,
            qualityCheck = check,
            errorMessage = if (!check.isValid) check.failureReasons.firstOrNull() else null
        )
    }

    fun completeProfileCreation(setAsActive: Boolean) {
        val curr = _enrollmentState.value
        val samples = curr.recordedAudioSamples ?: return
        val quality = curr.qualityCheck ?: return

        if (!quality.isValid) {
            _enrollmentState.value = curr.copy(errorMessage = "Cannot save voice profile with invalid audio quality.")
            return
        }

        val name = curr.profileName.ifBlank { "Custom Voice ${System.currentTimeMillis() % 1000}" }

        viewModelScope.launch(Dispatchers.IO) {
            val profileId = "custom_voice_${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val expiresAt = now + VoiceDataPrivacyManager.RETENTION_PERIOD_MS

            val (pitchMultiplier, speechRateMultiplier) =
                AudioQualityValidator.extractVoiceSynthesisParams(samples, 16000)

            val secureDir = privacyManager.getSecureSamplesDirectory()
            val sampleFile = File(secureDir, "${profileId}_raw.pcm")
            try {
                FileOutputStream(sampleFile).use { fos ->
                    val byteBuffer = ByteArray(samples.size * 2)
                    for (i in samples.indices) {
                        val s = (samples[i].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
                        byteBuffer[i * 2] = (s.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    fos.write(byteBuffer)
                }
            } catch (e: Exception) {
                // Ignore
            }

            val entity = OutputVoiceProfileEntity(
                id = profileId,
                displayName = name,
                sourceType = VoiceSourceType.RECORDED,
                createdAt = now,
                updatedAt = now,
                rawSamplePath = sampleFile.absolutePath,
                rawSampleExpiresAt = expiresAt,
                pitchMultiplier = pitchMultiplier,
                speechRateMultiplier = speechRateMultiplier,
                baseVoiceKey = "custom_neural_derived",
                supportedLanguages = "en,bn,hi",
                isLocalAvailable = true,
                isOnlineAvailable = false,
                isActive = setAsActive,
                consentConfirmed = true,
                consentConfirmedAt = now,
                sampleDurationMs = quality.durationMs,
                qualityScore = quality.overallScore,
                modelVersion = "neural-custom-v1.0"
            )

            db.outputVoiceDao().insertProfile(entity)

            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "OUTPUT_VOICE_CREATED",
                    details = "Custom output voice profile [$name] created with user consent. 10-day retention set. Voice quality: ${(quality.overallScore * 100).toInt()}%.",
                    diagnosticCode = "SEC_VOICE_CREATED"
                )
            )

            if (setAsActive) {
                outputVoiceManager.selectActiveProfile(profileId)
            }

            withContext(Dispatchers.Main) {
                _enrollmentState.value = OutputVoiceEnrollmentState(step = 1)
                _statusMessage.value = "Voice Profile '$name' created successfully! 10-day raw sample retention active."
            }
        }
    }

    fun selectOutputVoice(profileId: String) {
        viewModelScope.launch {
            outputVoiceManager.selectActiveProfile(profileId)
            _statusMessage.value = "Active speaking voice updated."
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val success = privacyManager.deleteVoiceProfile(profileId)
            if (success) {
                _statusMessage.value = "Profile and raw recordings permanently deleted."
            }
        }
    }

    fun setProcessingMode(mode: VoiceProcessingMode) {
        outputVoiceManager.setProcessingMode(mode)
        viewModelScope.launch {
            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "PROCESSING_MODE_CHANGED",
                    details = "Voice processing mode switched to [${mode.label}].",
                    diagnosticCode = "SEC_MODE_CHANGED"
                )
            )
        }
    }

    fun previewVoice(profile: OutputVoiceProfileEntity, language: VoiceLanguage) {
        viewModelScope.launch {
            outputVoiceManager.selectActiveProfile(profile.id)
            val testSentence = language.testSentence
            outputVoiceManager.speakText(testSentence, language)
        }
    }

    fun speakCustomText(text: String, language: VoiceLanguage) {
        if (text.isBlank()) return
        viewModelScope.launch {
            outputVoiceManager.speakText(text, language)
        }
    }

    fun stopSpeaking() {
        outputVoiceManager.stopSpeaking()
    }

    fun sendAssistantMessage(text: String, simulateSubham: Boolean = true) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val interaction = assistantBrain.processCommand(
                query = text,
                simulatedAudioSample = null,
                isSimulatedSubham = simulateSubham
            )
            _interactions.value = _interactions.value + interaction

            if (interaction.isSilentlyIgnored) {
                _statusMessage.value = "Silent Rejection: Speaker is not Subham (${interaction.diagnosticReason})"
            }
        }
    }

    fun addPersonalMemory(category: MemoryCategory, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.personalMemoryDao().insertMemory(
                PersonalMemoryEntity(
                    category = category,
                    content = content,
                    isEncrypted = true
                )
            )
            _statusMessage.value = "Memory saved to encrypted local storage."
        }
    }

    fun deletePersonalMemory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.personalMemoryDao().deleteMemoryById(id)
            _statusMessage.value = "Memory removed."
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            db.personalMemoryDao().clearAllMemories()
            _statusMessage.value = "All personal memories purged."
        }
    }

    fun addReminder(title: String, inMinutes: Int = 60) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = System.currentTimeMillis() + inMinutes * 60000L
            db.reminderDao().insertReminder(
                ReminderEntity(
                    title = title,
                    targetTimeMillis = target,
                    formattedTarget = "In $inMinutes minutes",
                    isHighRisk = false
                )
            )
            _statusMessage.value = "Reminder scheduled."
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.reminderDao().deleteReminderById(id)
        }
    }

    fun clearSynthesisCache() {
        viewModelScope.launch {
            val bytes = privacyManager.clearSynthesisCache()
            _statusMessage.value = "Synthesis audio cache cleared ($bytes bytes freed)."
        }
    }

    fun deleteAllVoiceSamples() {
        viewModelScope.launch {
            val count = privacyManager.deleteAllRawSamples()
            _statusMessage.value = "Purged $count raw voice recordings."
        }
    }

    fun deleteAllCustomProfiles() {
        viewModelScope.launch {
            privacyManager.deleteAllCustomProfiles()
            _statusMessage.value = "All custom voice profiles and biometric models purged."
        }
    }

    fun runRetentionCleanup() {
        viewModelScope.launch {
            val count = privacyManager.enforceRetentionPolicy()
            _statusMessage.value = if (count > 0) {
                "Retention check complete: $count expired sample(s) purged."
            } else {
                "Retention check complete: No expired samples found (all within 10-day limit)."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioCaptureManager.stopCapture()
        outputVoiceManager.release()
    }
}

package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.assistant.AssistantBrain
import com.example.core.assistant.AssistantInput
import com.example.core.assistant.AssistantInputSource
import com.example.core.assistant.AssistantInteraction
import com.example.core.assistant.AssistantOperatingMode
import com.example.core.assistant.GeminiDiagnosticsState
import com.example.core.assistant.OrchestratorTrace
import com.example.core.audio.AninAudioCaptureService
import com.example.core.audio.AninSpeechRecognizer
import com.example.core.audio.AudioCaptureManager
import com.example.core.audio.AudioCaptureMetrics
import com.example.core.audio.AudioHardwareCapabilities
import com.example.core.audio.MicrophoneHardwareTestResult
import com.example.core.audio.SpeechRecognitionStatus
import com.example.core.audio.WakeWordEngine
import com.example.core.auth.EnrollmentMetadata
import com.example.core.auth.EnrollmentSentence
import com.example.core.auth.SpeakerEnrollmentResult
import com.example.core.auth.SpeakerVerificationEngine
import com.example.core.auth.SpeakerVerificationEngineImpl
import com.example.core.auth.SubhamVoiceEnrollmentManager
import com.example.core.auth.VerificationResult
import com.example.core.database.AppDatabase
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.database.PersonalMemoryEntity
import com.example.core.database.ReminderEntity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.database.SubhamEnrollmentSampleEntity
import com.example.core.device.DeviceSystemDiagnostics
import com.example.core.model.AudioQualityCheck
import com.example.core.model.VoiceLanguage
import com.example.core.model.VoiceProcessingMode
import com.example.core.security.EncryptedDataStorageManager
import com.example.core.voice.AudioPlaybackManager
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

    // Speech Recognizer for real spoken voice commands
    val speechRecognizer = AninSpeechRecognizer(application) { recognizedQuery ->
        handleRecognizedSpokenCommand(recognizedQuery)
    }

    // Wake Word Engine: triggers real speech recognizer when "Hey Anin" is detected
    val wakeWordEngine = WakeWordEngine {
        onWakeWordDetected()
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
    val speechStatus: StateFlow<SpeechRecognitionStatus> = speechRecognizer.status

    val orchestrator get() = assistantBrain.orchestrator
    val operatingMode: StateFlow<AssistantOperatingMode> = orchestrator.operatingMode
    val lastTrace: StateFlow<OrchestratorTrace?> = orchestrator.lastTrace
    val recentTraces: StateFlow<List<OrchestratorTrace>> = orchestrator.recentTraces
    val geminiDiagnostics: StateFlow<GeminiDiagnosticsState> = orchestrator.geminiApiClient.diagnostics

    fun setOperatingMode(mode: AssistantOperatingMode) {
        orchestrator.setOperatingMode(mode)
        _statusMessage.value = "Anin intelligence mode switched to: ${mode.name}"
    }

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

    private val _isMicrophonePermissionGranted = MutableStateFlow(audioCaptureManager.hasMicrophonePermission())
    val isMicrophonePermissionGranted: StateFlow<Boolean> = _isMicrophonePermissionGranted.asStateFlow()

    // Recording and test states
    private val _isRecordingEnrollment = MutableStateFlow(false)
    val isRecordingEnrollment: StateFlow<Boolean> = _isRecordingEnrollment.asStateFlow()

    private val _recordingSentenceIndex = MutableStateFlow<Int?>(null)
    val recordingSentenceIndex: StateFlow<Int?> = _recordingSentenceIndex.asStateFlow()

    private val _enrollmentRecordingProgress = MutableStateFlow(0f)
    val enrollmentRecordingProgress: StateFlow<Float> = _enrollmentRecordingProgress.asStateFlow()

    private val _enrollmentLiveDbfs = MutableStateFlow(-90f)
    val enrollmentLiveDbfs: StateFlow<Float> = _enrollmentLiveDbfs.asStateFlow()

    private val _microphoneTestResult = MutableStateFlow<MicrophoneHardwareTestResult?>(null)
    val microphoneTestResult: StateFlow<MicrophoneHardwareTestResult?> = _microphoneTestResult.asStateFlow()

    private val _isTestingMicrophone = MutableStateFlow(false)
    val isTestingMicrophone: StateFlow<Boolean> = _isTestingMicrophone.asStateFlow()

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

            launch {
                speechRecognizer.status.collect { status ->
                    if (status.state == SpeechRecognitionState.ERROR && status.errorMessage != null) {
                        _statusMessage.value = "Speech recognition: ${status.errorMessage}"
                        startWakeWordListening()
                    }
                }
            }

            val purgedCount = privacyManager.enforceRetentionPolicy()
            if (purgedCount > 0) {
                _statusMessage.value = "10-day retention check: $purgedCount expired voice sample(s) automatically purged."
            }

            if (audioCaptureManager.hasMicrophonePermission()) {
                startWakeWordListening()
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _isMicrophonePermissionGranted.value = isGranted
        if (isGranted) {
            startWakeWordListening()
        } else {
            audioCaptureManager.stopCapture()
            _statusMessage.value = "Microphone permission denied. Voice input is unavailable."
        }
    }

    fun startWakeWordListening() {
        if (!_isMicrophonePermissionGranted.value) return
        val started = audioCaptureManager.startCapture()
        if (started) {
            try {
                AninAudioCaptureService.startService(getApplication())
                _isForegroundServiceActive.value = true
            } catch (e: Exception) {}
        }
    }

    private fun onWakeWordDetected() {
        _wakeWordTriggerCount.value += 1
        _statusMessage.value = "Hey Anin detected! Listening for Subham..."
        // Temporarily pause wake-word capture while speech recognizer handles the microphone
        audioCaptureManager.stopCapture()
        speechRecognizer.startListening()
    }

    fun startListeningForVoiceCommand() {
        if (!audioCaptureManager.hasMicrophonePermission()) {
            _statusMessage.value = "Microphone permission is required. Please grant it in Permissions."
            return
        }
        audioCaptureManager.stopCapture()
        speechRecognizer.startListening()
    }

    fun stopListeningForVoiceCommand() {
        speechRecognizer.stopListening()
        startWakeWordListening()
    }

    private fun handleRecognizedSpokenCommand(query: String) {
        viewModelScope.launch {
            _statusMessage.value = "Command: \"$query\""
            sendAssistantMessage(query, isSimulatedSubham = true)
            // Resume wake-word listening after command is dispatched
            startWakeWordListening()
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
                try {
                    AninAudioCaptureService.startService(getApplication())
                    _isForegroundServiceActive.value = true
                } catch (e: Exception) {}
                _statusMessage.value = "Microphone active. Wake-word detection running."
            } else {
                _statusMessage.value = "Could not start audio capture. Please check microphone permission."
            }
        } else {
            audioCaptureManager.stopCapture()
            try {
                AninAudioCaptureService.stopService(getApplication())
                _isForegroundServiceActive.value = false
            } catch (e: Exception) {}
            _statusMessage.value = "Audio capture paused."
        }
    }

    fun triggerWakeWordManually() {
        wakeWordEngine.triggerWakeWordManually()
    }

    /**
     * Real Subham Voice Enrollment:
     * Records REAL audio from microphone for 3 seconds.
     * STRICT FAIL-CLOSED RULE: If silence or no speech is heard, REJECTS immediately!
     */
    fun enrollSubhamSentenceSample(sentence: EnrollmentSentence, sampleRate: Int = 16000) {
        viewModelScope.launch {
            if (!audioCaptureManager.hasMicrophonePermission()) {
                _statusMessage.value = "Microphone permission is not granted. Please allow microphone access."
                return@launch
            }

            // Stop continuous wake-word capture so AudioRecord has exclusive microphone hardware access
            audioCaptureManager.stopCapture()

            _isRecordingEnrollment.value = true
            _recordingSentenceIndex.value = sentence.index
            _enrollmentRecordingProgress.value = 0f
            _enrollmentLiveDbfs.value = -90f
            _statusMessage.value = "Recording sentence #${sentence.index}... Speak now: \"${sentence.text}\""

            val recordResult = audioCaptureManager.recordRealPcmSample(
                durationSeconds = 3.0f,
                onProgress = { progress, dbfs ->
                    _enrollmentRecordingProgress.value = progress
                    _enrollmentLiveDbfs.value = dbfs
                }
            )

            _isRecordingEnrollment.value = false
            _recordingSentenceIndex.value = null

            recordResult.fold(
                onSuccess = { realSamples ->
                    val check = subhamEnrollmentManager.enrollSample(sentence, realSamples, sampleRate)
                    if (check.isValid) {
                        _statusMessage.value = "Sentence #${sentence.index} enrolled (${sentence.language.displayName})! Quality: ${(check.overallScore * 100).toInt()}%"
                    } else {
                        val reason = check.failureReasons.firstOrNull() ?: "Didn't hear speech."
                        _statusMessage.value = "Sentence #${sentence.index} REJECTED: $reason"
                    }
                },
                onFailure = { error ->
                    _statusMessage.value = "Sentence #${sentence.index} REJECTED: ${error.message}"
                }
            )

            // Resume wake-word monitoring after enrollment recording finishes
            startWakeWordListening()
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

    fun clearSubhamBiometrics() {
        viewModelScope.launch {
            subhamEnrollmentManager.clearAllSubhamEnrollments()
            refreshAuthStatus()
            _statusMessage.value = "Subham speaker biometric authorization and samples cleared."
        }
    }

    /**
     * Real Physical Device Microphone Diagnostics Test (Section P)
     */
    fun runMicrophoneHardwareTest() {
        viewModelScope.launch {
            if (!audioCaptureManager.hasMicrophonePermission()) {
                _statusMessage.value = "Microphone permission required for test."
                return@launch
            }

            audioCaptureManager.stopCapture()
            _isTestingMicrophone.value = true
            _statusMessage.value = "Testing microphone hardware on iQOO Neo 10R... Speak now for 3 seconds!"

            val result = audioCaptureManager.runMicrophoneDiagnosticsTest(3.0f)
            _isTestingMicrophone.value = false
            _microphoneTestResult.value = result
            _statusMessage.value = result.message

            startWakeWordListening()
        }
    }

    fun sendAssistantMessage(query: String, isSimulatedSubham: Boolean = true) {
        viewModelScope.launch {
            val interaction = assistantBrain.processCommand(
                query = query,
                simulatedAudioSample = null,
                isSimulatedSubham = isSimulatedSubham
            )

            _interactions.value = listOf(interaction) + _interactions.value
        }
    }

    fun stopSpeaking() {
        audioPlaybackManager.bargeInEmergencyStop()
        _statusMessage.value = "Emergency Stop: Anin speech interrupted immediately."
    }

    fun setActiveProfile(profile: OutputVoiceProfileEntity) {
        viewModelScope.launch {
            outputVoiceManager.selectProfile(profile.id)
            _statusMessage.value = "Active output voice profile set to: ${profile.displayName}"
        }
    }

    fun setProcessingMode(mode: VoiceProcessingMode) {
        viewModelScope.launch {
            outputVoiceManager.setProcessingMode(mode)
            _statusMessage.value = "Voice processing mode switched to: ${mode.name}"
        }
    }

    fun deleteProfile(profile: OutputVoiceProfileEntity) {
        viewModelScope.launch {
            val success = outputVoiceManager.deleteProfile(profile.id)
            if (success) {
                _statusMessage.value = "Profile '${profile.displayName}' deleted."
            } else {
                _statusMessage.value = "Cannot delete active or built-in voice profile."
            }
        }
    }

    fun previewProfile(profile: OutputVoiceProfileEntity) {
        outputVoiceManager.previewProfile(profile)
    }

    fun deleteMemory(memory: PersonalMemoryEntity) {
        viewModelScope.launch {
            db.personalMemoryDao().deleteMemory(memory)
            _statusMessage.value = "Memory '${memory.key}' removed from local vault."
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            db.personalMemoryDao().deleteAll()
            _statusMessage.value = "All personal memories wiped from hardware-backed encrypted vault."
        }
    }

    fun completeReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            db.reminderDao().updateReminder(reminder.copy(isCompleted = true))
            _statusMessage.value = "Reminder '${reminder.title}' marked as completed."
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            db.reminderDao().deleteReminder(reminder)
            _statusMessage.value = "Reminder '${reminder.title}' deleted."
        }
    }

    fun deleteAllVoiceSamples() {
        viewModelScope.launch {
            val count = privacyManager.deleteAllVoiceSamples()
            _statusMessage.value = "Deleted $count raw voice recording files from device storage."
        }
    }

    fun deleteAllCustomProfiles() {
        viewModelScope.launch {
            outputVoiceManager.deleteAllCustomProfiles()
            _statusMessage.value = "All custom voice profiles and biometric models reset to default built-ins."
        }
    }
}

package com.example.core.voice

import android.content.Context
import android.util.Log
import com.example.core.database.AppDatabase
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage
import com.example.core.model.VoiceProcessingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class OutputVoiceManager(
    private val context: Context,
    val audioPlaybackManager: AudioPlaybackManager
) {
    companion object {
        private const val TAG = "OutputVoiceManager"
    }

    private val db = AppDatabase.getInstance(context)

    val localEngine = LocalOutputVoiceEngine(context) { speaking ->
        audioPlaybackManager.setAssistantSpeaking(speaking)
    }

    val onlineEngine = OnlineOutputVoiceEngine(context, localEngine)

    private val _activeProfile = MutableStateFlow<OutputVoiceProfileEntity?>(null)
    val activeProfile: StateFlow<OutputVoiceProfileEntity?> = _activeProfile.asStateFlow()

    private val _processingMode = MutableStateFlow(VoiceProcessingMode.AUTOMATIC)
    val processingMode: StateFlow<VoiceProcessingMode> = _processingMode.asStateFlow()

    private val _latestSynthesisResult = MutableStateFlow<SynthesisResult?>(null)
    val latestSynthesisResult: StateFlow<SynthesisResult?> = _latestSynthesisResult.asStateFlow()

    private val _isCustomVoiceActive = MutableStateFlow(false)
    val isCustomVoiceActive: StateFlow<Boolean> = _isCustomVoiceActive.asStateFlow()

    private val _isFallbackActive = MutableStateFlow(false)
    val isFallbackActive: StateFlow<Boolean> = _isFallbackActive.asStateFlow()

    init {
        audioPlaybackManager.registerStopAction {
            localEngine.stopPlayback()
            onlineEngine.stopPlayback()
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        localEngine.initialize()
        val current = db.outputVoiceDao().getActiveProfile()
            ?: db.outputVoiceDao().getProfileById("anin_default_neural")
        current?.let { loadProfile(it) }
    }

    fun setProcessingMode(mode: VoiceProcessingMode) {
        _processingMode.value = mode
    }

    suspend fun selectActiveProfile(profileId: String): Boolean = withContext(Dispatchers.IO) {
        val profile = db.outputVoiceDao().getProfileById(profileId) ?: return@withContext false
        db.outputVoiceDao().setActiveProfile(profileId)
        loadProfile(profile)

        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "PROFILE_SELECTED",
                details = "Active output voice profile set to [${profile.displayName}].",
                diagnosticCode = "SEC_PROFILE_SELECTED"
            )
        )
        true
    }

    private suspend fun loadProfile(profile: OutputVoiceProfileEntity) = withContext(Dispatchers.Main) {
        _activeProfile.value = profile
        _isCustomVoiceActive.value = profile.sourceType != com.example.core.model.VoiceSourceType.BUILT_IN
        localEngine.loadActiveVoiceProfile(profile)
        onlineEngine.loadActiveVoiceProfile(profile)
    }

    /**
     * Synthesizes and speaks text using active voice and honest fallback policy.
     */
    suspend fun speakText(
        text: String,
        forcedLanguage: VoiceLanguage? = null
    ): SynthesisResult = withContext(Dispatchers.Main) {
        val detectedLanguage = forcedLanguage ?: VoiceLanguage.detectLanguage(text)
        val mode = _processingMode.value
        val profile = _activeProfile.value

        Log.i(TAG, "Initiating speech synthesis. Lang: ${detectedLanguage.displayName}, Mode: ${mode.name}")

        _isFallbackActive.value = false

        // Determine which engine to attempt first
        val primaryResult: SynthesisResult = when (mode) {
            VoiceProcessingMode.ONLINE_ONLY -> {
                onlineEngine.synthesizeText(text, detectedLanguage)
            }
            VoiceProcessingMode.OFFLINE_ONLY -> {
                localEngine.synthesizeText(text, detectedLanguage)
            }
            VoiceProcessingMode.AUTOMATIC -> {
                // If local engine is available, use it directly
                val localAvailability = localEngine.reportAvailability()
                if (localAvailability is EngineAvailability.Available) {
                    localEngine.synthesizeText(text, detectedLanguage)
                } else if (onlineEngine.reportAvailability() is EngineAvailability.Available) {
                    onlineEngine.synthesizeText(text, detectedLanguage)
                } else {
                    localEngine.synthesizeText(text, detectedLanguage)
                }
            }
        }

        // If primary succeeded, record and return
        if (primaryResult.isSuccess) {
            _latestSynthesisResult.value = primaryResult
            return@withContext primaryResult
        }

        // If primary failed or language unsupported in custom voice:
        // Execute Fallback Policy:
        Log.w(TAG, "Primary synthesis failed: ${primaryResult.error?.code}. Applying system fallback voice.")

        _isFallbackActive.value = true
        val fallbackVoiceName = "System Standard (Fallback)"

        // Fallback always synthesizes in target language or English safely
        val fallbackResult = localEngine.synthesizeText(text, VoiceLanguage.ENGLISH)
        val finalResult = fallbackResult.copy(
            isFallback = true,
            voiceUsed = fallbackVoiceName,
            error = OutputVoiceError.FallbackActivated(fallbackVoiceName)
        )

        _latestSynthesisResult.value = finalResult

        // Log fallback event in security audit
        withContext(Dispatchers.IO) {
            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "FALLBACK_ACTIVATED",
                    details = "Primary output voice failed (${primaryResult.error?.code}). Fallback system voice activated.",
                    diagnosticCode = "OUTPUT_VOICE_FALLBACK_ACTIVATED"
                )
            )
        }

        finalResult
    }

    fun stopSpeaking() {
        localEngine.stopPlayback()
        onlineEngine.stopPlayback()
        audioPlaybackManager.setAssistantSpeaking(false)
    }

    fun release() {
        localEngine.releaseResources()
        onlineEngine.releaseResources()
    }
}

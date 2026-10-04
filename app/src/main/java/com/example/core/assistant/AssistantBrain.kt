package com.example.core.assistant

import android.content.Context
import com.example.core.auth.SpeakerVerificationEngine
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.DeviceDiagnosticsManager
import com.example.core.model.VoiceLanguage
import com.example.core.voice.AudioPlaybackManager
import com.example.core.voice.OutputVoiceManager

data class AssistantInteraction(
    val query: String,
    val response: String,
    val detectedLanguage: VoiceLanguage,
    val isSubhamAuthorized: Boolean,
    val verificationConfidence: Float,
    val isSilentlyIgnored: Boolean = false,
    val diagnosticReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class AssistantBrain(
    private val context: Context,
    private val speakerVerificationEngine: SpeakerVerificationEngine,
    private val outputVoiceManager: OutputVoiceManager,
    private val audioPlaybackManager: AudioPlaybackManager
) {
    val orchestrator = AninHybridOrchestrator(
        context = context,
        speakerVerificationEngine = speakerVerificationEngine,
        outputVoiceManager = outputVoiceManager,
        audioPlaybackManager = audioPlaybackManager
    )

    val diagnosticsManager: DeviceDiagnosticsManager get() = orchestrator.diagnosticsManager
    val actionExecutor: DeviceActionExecutor get() = orchestrator.actionExecutor
    val capabilityRegistry: CapabilityRegistry get() = orchestrator.capabilityRegistry
    val geminiApiClient: GeminiApiClient get() = orchestrator.geminiApiClient

    companion object {
        fun detectLanguage(text: String): VoiceLanguage {
            return VoiceLanguage.detectLanguage(text)
        }
    }

    /**
     * Unified Canonical Assistant Processing Pipeline
     * Both Voice Input and Typed Text Input pass through this exact method.
     */
    suspend fun processInput(input: AssistantInput): AssistantInteraction {
        return orchestrator.processInput(input)
    }

    /**
     * Backward-compatible bridge calling the canonical processInput method.
     */
    suspend fun processCommand(
        query: String,
        simulatedAudioSample: FloatArray? = null,
        isSimulatedSubham: Boolean = true
    ): AssistantInteraction {
        val lang = detectLanguage(query)
        val normalized = AssistantIntentEngine.normalizeText(query)
        val input = AssistantInput(
            source = if (simulatedAudioSample != null) AssistantInputSource.VOICE else AssistantInputSource.TEXT,
            rawText = query,
            normalizedText = normalized,
            language = lang,
            confidence = 1.0f,
            isAuthorized = isSimulatedSubham,
            audioSamples = simulatedAudioSample
        )
        return processInput(input)
    }
}

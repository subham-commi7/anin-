package com.example.core.voice

import android.content.Context
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage

class OnlineOutputVoiceEngine(
    private val context: Context,
    private val fallbackLocalEngine: LocalOutputVoiceEngine
) : OutputVoiceEngine {

    private var activeProfile: OutputVoiceProfileEntity? = null

    override suspend fun initialize() {
        // Ready for future secure cloud speech provider integration
    }

    override suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity) {
        activeProfile = profile
    }

    override suspend fun synthesizeText(
        text: String,
        language: VoiceLanguage
    ): SynthesisResult {
        // Step 1: Privacy Architecture mandates local-first execution.
        // If online provider is not actively provisioned, seamlessly route to local engine.
        return fallbackLocalEngine.synthesizeText(text, language)
    }

    override fun stopPlayback() {
        fallbackLocalEngine.stopPlayback()
    }

    override fun reportAvailability(): EngineAvailability {
        return EngineAvailability.Unavailable("Online speech provider not configured in Step 1 (Local-First)")
    }

    override fun releaseResources() {
        // No network resources to release
    }
}

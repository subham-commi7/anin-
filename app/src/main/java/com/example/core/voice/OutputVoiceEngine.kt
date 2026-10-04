package com.example.core.voice

import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage

sealed class EngineAvailability {
    object Available : EngineAvailability()
    data class Unavailable(val reason: String) : EngineAvailability()
}

interface OutputVoiceEngine {
    suspend fun initialize()
    suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity)
    suspend fun synthesizeText(text: String, language: VoiceLanguage): SynthesisResult
    fun stopPlayback()
    fun reportAvailability(): EngineAvailability
    fun releaseResources()
}

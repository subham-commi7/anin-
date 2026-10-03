package com.example.core.voice

import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage

sealed class EngineAvailability {
    object Available : EngineAvailability()
    data class Unavailable(val reason: String) : EngineAvailability()
}

sealed class ModelLoadingState {
    object Unloaded : ModelLoadingState()
    object Loading : ModelLoadingState()
    data class Loaded(val voiceId: String, val language: VoiceLanguage) : ModelLoadingState()
    data class Error(val message: String) : ModelLoadingState()
}

interface OutputVoiceEngine {
    val engineName: String
    val isOnline: Boolean

    suspend fun initialize(): Boolean
    suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity): Boolean
    suspend fun synthesizeText(
        text: String,
        language: VoiceLanguage,
        onAudioReady: ((ByteArray) -> Unit)? = null
    ): SynthesisResult
    fun stopPlayback()
    fun reportAvailability(): EngineAvailability
    fun reportModelLoadingState(): ModelLoadingState
    fun exposeSupportedLanguages(): List<VoiceLanguage>
    fun releaseResources()
}

package com.example.core.assistant

import com.example.core.model.VoiceLanguage

enum class AssistantInputSource {
    VOICE,
    TEXT,
    SYSTEM
}

data class AssistantInput(
    val source: AssistantInputSource,
    val rawText: String,
    val normalizedText: String,
    val language: VoiceLanguage,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val isAuthorized: Boolean = false,
    val audioSamples: FloatArray? = null
)

package com.example.core.model

enum class VoiceLanguage(val code: String, val displayName: String, val samplePrompt: String, val testSentence: String) {
    ENGLISH("en", "English", "The quick brown fox jumps over the lazy dog.", "Hello Subham, I am Anin. How can I assist you today?"),
    BENGALI("bn", "বাংলা (Bengali)", "আজকের আকাশ পরিষ্কার এবং বাতাস শান্ত।", "নমস্কার শুভম, আমি অনিন। আপনি কেমন আছেন?"),
    HINDI("hi", "हिन्दी (Hindi)", "ज्ञान वह प्रकाश है जो जीवन को प्रकाशित करता है।", "नमस्ते शुभम, मैं अनिन हूँ। मैं आपकी क्या मदद करूँ?");

    companion object {
        fun fromCode(code: String): VoiceLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }

        fun detectLanguage(text: String): VoiceLanguage {
            var bengaliCount = 0
            var hindiCount = 0
            for (char in text) {
                val codePoint = char.code
                if (codePoint in 0x0980..0x09FF) {
                    bengaliCount++
                } else if (codePoint in 0x0900..0x097F) {
                    hindiCount++
                }
            }
            return when {
                bengaliCount > 0 && bengaliCount >= hindiCount -> BENGALI
                hindiCount > 0 -> HINDI
                else -> ENGLISH
            }
        }
    }
}

enum class VoiceProcessingMode(val label: String, val description: String) {
    AUTOMATIC("Automatic", "Prefers local custom voice, falls back safely to system engine."),
    OFFLINE_ONLY("Offline Only", "Strictly on-device local synthesis. Zero network transmission."),
    ONLINE_ONLY("Online Only", "Configured online provider only. Requires network and explicit consent.")
}

enum class VoiceSourceType {
    RECORDED,
    IMPORTED,
    BUILT_IN
}

data class AudioQualityCheck(
    val isValid: Boolean,
    val sampleRate: Int,
    val channelCount: Int,
    val durationMs: Long,
    val rmsLevelDb: Float,
    val clippingDetected: Boolean,
    val silenceRatio: Float,
    val estimatedSnrDb: Float,
    val speechDetected: Boolean,
    val failureReasons: List<String> = emptyList(),
    val overallScore: Float = 0.85f
)

sealed class OutputVoiceError(val code: String, val userMessage: String) {
    object NotEnrolled : OutputVoiceError("OUTPUT_VOICE_NOT_ENROLLED", "No custom output voice profile is enrolled.")
    object ModelNotFound : OutputVoiceError("OUTPUT_VOICE_MODEL_NOT_FOUND", "The voice synthesis model file could not be found.")
    object ModelCorrupted : OutputVoiceError("OUTPUT_VOICE_MODEL_CORRUPTED", "The voice profile representation data is corrupted.")
    data class UnsupportedLanguage(val lang: String) : OutputVoiceError("OUTPUT_VOICE_UNSUPPORTED_LANGUAGE", "Language '$lang' is not supported by this voice profile.")
    data class SynthesisFailed(val details: String) : OutputVoiceError("OUTPUT_VOICE_SYNTHESIS_FAILED", "Speech synthesis failed: $details")
    object NetworkRequired : OutputVoiceError("OUTPUT_VOICE_NETWORK_REQUIRED", "Online voice synthesis requires an active network connection.")
    object ProviderDisabled : OutputVoiceError("OUTPUT_VOICE_PROVIDER_DISABLED", "The requested voice engine provider is currently disabled.")
    object PolicyError : OutputVoiceError("OUTPUT_VOICE_PERMISSION_OR_POLICY_ERROR", "Consent or authorization policy requirement not satisfied.")
    object PlaybackFailed : OutputVoiceError("OUTPUT_AUDIO_PLAYBACK_FAILED", "Audio track playback encountered a hardware or pipeline error.")
    data class FallbackActivated(val fallbackVoiceName: String) : OutputVoiceError("OUTPUT_VOICE_FALLBACK_ACTIVATED", "Primary voice unavailable. System fallback voice ($fallbackVoiceName) activated.")
}

data class SynthesisResult(
    val isSuccess: Boolean,
    val audioDurationMs: Long = 0,
    val latencyMs: Long = 0,
    val voiceUsed: String,
    val language: VoiceLanguage,
    val isFallback: Boolean = false,
    val isLocal: Boolean = true,
    val error: OutputVoiceError? = null
)

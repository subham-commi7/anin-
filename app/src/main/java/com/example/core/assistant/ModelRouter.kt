package com.example.core.assistant

import com.example.core.model.VoiceLanguage

enum class RoutingTarget {
    LOCAL,
    GEMINI,
    HYBRID
}

data class RoutingDecision(
    val target: RoutingTarget,
    val localIntent: RecognizedIntent? = null,
    val reason: String,
    val confidence: Float = 1.0f
)

object ModelRouter {

    fun route(
        normalizedText: String,
        language: VoiceLanguage,
        intent: RecognizedIntent,
        operatingMode: AssistantOperatingMode = AssistantOperatingMode.HYBRID
    ): RoutingDecision {
        // If mode is forced LOCAL_ONLY or OFFLINE, always stay local
        if (operatingMode == AssistantOperatingMode.LOCAL_ONLY || operatingMode == AssistantOperatingMode.OFFLINE) {
            return RoutingDecision(
                target = RoutingTarget.LOCAL,
                localIntent = intent,
                reason = "FORCED_BY_MODE_${operatingMode.name}",
                confidence = 1.0f
            )
        }

        // 1. Immediate Stop / Barge-in -> strictly LOCAL
        if (intent.type == AssistantIntentType.STOP_HALT) {
            return RoutingDecision(
                target = RoutingTarget.LOCAL,
                localIntent = intent,
                reason = "DETERMINISTIC_STOP",
                confidence = 1.0f
            )
        }

        // 2. Multi-intent / complex compound sentences -> GEMINI / HYBRID
        // e.g. "YouTube খুলে দাও আর পরে আমাকে মনে করিয়ে দিও..." ("and", "এবং", "আর", "aur", "and also")
        if (isCompoundOrMultiIntent(normalizedText)) {
            return RoutingDecision(
                target = RoutingTarget.HYBRID,
                localIntent = intent,
                reason = "COMPOUND_OR_MULTI_ACTION_PLANNING",
                confidence = 0.95f
            )
        }

        // 3. Deterministic Local Operations (0 Gemini calls)
        when (intent.type) {
            AssistantIntentType.BATTERY_STATUS -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_HARDWARE_BATTERY",
                    confidence = 1.0f
                )
            }
            AssistantIntentType.OPEN_APP, AssistantIntentType.PLAY_YOUTUBE -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_APP_LAUNCH",
                    confidence = 0.98f
                )
            }
            AssistantIntentType.TIME_DATE -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_CLOCK_TELEMETRY",
                    confidence = 1.0f
                )
            }
            AssistantIntentType.HEAR_CHECK -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_AUDIO_CHECK",
                    confidence = 1.0f
                )
            }
            AssistantIntentType.IDENTITY_CHECK -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_IDENTITY_CHECK",
                    confidence = 1.0f
                )
            }
            AssistantIntentType.DEVICE_DIAGNOSTICS -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_DEVICE_DIAGNOSTICS",
                    confidence = 1.0f
                )
            }
            AssistantIntentType.GREETING -> {
                // Short simple greetings stay local for zero latency
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_FAST_GREETING",
                    confidence = 0.95f
                )
            }
            AssistantIntentType.REMINDER_CREATE, AssistantIntentType.MEMORY_STORE -> {
                // If it's a simple local reminder or memory command without complex reasoning
                if (normalizedText.length < 50 && !normalizedText.contains("explain") && !normalizedText.contains("routine")) {
                    return RoutingDecision(
                        target = RoutingTarget.LOCAL,
                        localIntent = intent,
                        reason = "DETERMINISTIC_LOCAL_STORAGE",
                        confidence = 0.90f
                    )
                }
            }
            AssistantIntentType.CALL_CONTACT -> {
                return RoutingDecision(
                    target = RoutingTarget.LOCAL,
                    localIntent = intent,
                    reason = "DETERMINISTIC_PHONE_DIALER",
                    confidence = 0.95f
                )
            }
            else -> {}
        }

        // 4. Conversational / Complex / Knowledge / Advice -> GEMINI
        return RoutingDecision(
            target = RoutingTarget.GEMINI,
            localIntent = intent,
            reason = "GENERAL_REASONING_OR_KNOWLEDGE",
            confidence = 0.95f
        )
    }

    private fun isCompoundOrMultiIntent(text: String): Boolean {
        val compoundConjunctions = listOf(
            " এবং ", " আর পরে ", " আর তারপর ", " এবং তারপর ", " তার সাথে ",
            " and then ", " and also ", " and remind ", " and after ", " and later ",
            " और फिर ", " और बाद में ", " और साथ ही "
        )
        return compoundConjunctions.any { text.contains(it) }
    }
}

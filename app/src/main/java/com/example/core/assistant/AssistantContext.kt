package com.example.core.assistant

import com.example.core.model.VoiceLanguage

data class AssistantContext(
    val userDisplayName: String = "Subham",
    val preferredLanguage: VoiceLanguage = VoiceLanguage.BENGALI,
    val deviceModel: String = "iQOO Neo 10R (Android 16)",
    val availableCapabilities: List<String> = emptyList(),
    val batteryPercentage: Int = 100,
    val isCharging: Boolean = false,
    val networkStatus: String = "Connected",
    val relevantMemories: List<String> = emptyList(),
    val recentInteractions: List<Pair<String, String>> = emptyList(),
    val isSubhamAuthenticated: Boolean = true
) {
    /**
     * Sanitized summary for Gemini context injection.
     * Guaranteed not to include private encryption keys, biometric vectors, or raw audio.
     */
    fun toGeminiContextPrompt(): String {
        val memorySnippets = if (relevantMemories.isNotEmpty()) {
            "Relevant User Memories & Preferences:\n" + relevantMemories.joinToString("\n") { "- $it" }
        } else {
            "No specific relevant user memories."
        }

        val recentHistory = if (recentInteractions.isNotEmpty()) {
            "Recent Conversation History:\n" + recentInteractions.takeLast(3).joinToString("\n") { (q, a) ->
                "User: $q\nAnin: $a"
            }
        } else {
            "No recent conversation history."
        }

        return """
            USER_INFO:
            - User Name: $userDisplayName
            - Current Language: ${preferredLanguage.displayName} (${preferredLanguage.code})
            - Device: $deviceModel
            - Battery: $batteryPercentage% (${if (isCharging) "Charging" else "On Battery"})
            - Network: $networkStatus
            - Authenticated: $isSubhamAuthenticated

            $memorySnippets

            $recentHistory
        """.trimIndent()
    }
}

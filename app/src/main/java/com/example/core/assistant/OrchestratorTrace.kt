package com.example.core.assistant

import com.example.core.model.VoiceLanguage

data class OrchestratorTrace(
    val id: String = "trace_${System.currentTimeMillis()}",
    val timestamp: Long = System.currentTimeMillis(),
    val source: AssistantInputSource,
    val language: VoiceLanguage,
    val transcript: String,
    val normalizedText: String,
    val routingTarget: RoutingTarget,
    val routingReason: String,
    val localIntent: AssistantIntentType?,
    val localConfidence: Float,
    val geminiUsed: Boolean,
    val geminiLatencyMs: Long? = null,
    val capabilitiesInvolved: List<String> = emptyList(),
    val actionExecuted: String? = null,
    val actionResult: String? = null,
    val finalResponse: String,
    val isSilent: Boolean = false,
    val totalDurationMs: Long = 0L
) {
    fun toFormattedLog(): String {
        return """
            [ANIN TRACE $id]
            INPUT_RECEIVED: source=$source, lang=${language.code}, text="$transcript"
            ROUTING: target=$routingTarget, reason=$routingReason
            LOCAL_INTENT: $localIntent (conf=$localConfidence)
            GEMINI_USED: $geminiUsed (latency=${geminiLatencyMs ?: 0}ms)
            CAPABILITIES: ${capabilitiesInvolved.joinToString(", ")}
            ACTION: $actionExecuted -> $actionResult
            RESPONSE: "$finalResponse" (Duration: ${totalDurationMs}ms)
        """.trimIndent()
    }
}

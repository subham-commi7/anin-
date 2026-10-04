package com.example.core.assistant

import org.json.JSONArray
import org.json.JSONObject

enum class GeminiResponseType {
    ACTION,
    MULTI_ACTION,
    CONVERSATION,
    CLARIFICATION,
    SAFETY_REFUSAL,
    UNKNOWN
}

data class PlannedAction(
    val capability: String,
    val intent: String,
    val arguments: Map<String, String> = emptyMap()
)

data class GeminiStructuredOutput(
    val type: GeminiResponseType,
    val answer: String? = null,
    val actions: List<PlannedAction> = emptyList(),
    val clarificationQuestion: String? = null,
    val rawResponse: String = ""
) {
    companion object {
        fun parse(rawText: String): GeminiStructuredOutput {
            val trimmed = rawText.trim()

            // 1. Strip markdown code block if present: ```json ... ```
            val jsonCandidate = when {
                trimmed.contains("```json") -> {
                    trimmed.substringAfter("```json").substringBefore("```").trim()
                }
                trimmed.contains("```") -> {
                    trimmed.substringAfter("```").substringBefore("```").trim()
                }
                trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                    trimmed
                }
                else -> {
                    // Check if JSON object is embedded somewhere in text
                    val startIdx = trimmed.indexOf('{')
                    val endIdx = trimmed.lastIndexOf('}')
                    if (startIdx != -1 && endIdx > startIdx) {
                        trimmed.substring(startIdx, endIdx + 1).trim()
                    } else null
                }
            }

            if (jsonCandidate != null) {
                try {
                    val obj = JSONObject(jsonCandidate)
                    val typeStr = obj.optString("type", "CONVERSATION").uppercase()

                    return when (typeStr) {
                        "ACTION" -> {
                            val cap = obj.optString("capability", "").lowercase()
                            val intent = obj.optString("intent", "OPEN_APP")
                            val argsObj = obj.optJSONObject("arguments")
                            val argsMap = mutableMapOf<String, String>()
                            if (argsObj != null) {
                                val keys = argsObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    argsMap[k] = argsObj.optString(k, "")
                                }
                            }
                            val spokenResponse = obj.optString("response", obj.optString("answer", ""))
                            GeminiStructuredOutput(
                                type = GeminiResponseType.ACTION,
                                answer = if (spokenResponse.isNotBlank()) spokenResponse else null,
                                actions = listOf(PlannedAction(cap, intent, argsMap)),
                                rawResponse = trimmed
                            )
                        }
                        "MULTI_ACTION" -> {
                            val actionList = mutableListOf<PlannedAction>()
                            val arr = obj.optJSONArray("actions") ?: JSONArray()
                            for (i in 0 until arr.length()) {
                                val actObj = arr.optJSONObject(i) ?: continue
                                val cap = actObj.optString("capability", "").lowercase()
                                val intent = actObj.optString("intent", "")
                                val argsObj = actObj.optJSONObject("arguments")
                                val argsMap = mutableMapOf<String, String>()
                                if (argsObj != null) {
                                    val keys = argsObj.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        argsMap[k] = argsObj.optString(k, "")
                                    }
                                }
                                actionList.add(PlannedAction(cap, intent, argsMap))
                            }
                            val spoken = obj.optString("response", obj.optString("answer", ""))
                            GeminiStructuredOutput(
                                type = GeminiResponseType.MULTI_ACTION,
                                answer = if (spoken.isNotBlank()) spoken else null,
                                actions = actionList,
                                rawResponse = trimmed
                            )
                        }
                        "CLARIFICATION" -> {
                            val q = obj.optString("question", obj.optString("answer", trimmed))
                            GeminiStructuredOutput(
                                type = GeminiResponseType.CLARIFICATION,
                                clarificationQuestion = q,
                                answer = q,
                                rawResponse = trimmed
                            )
                        }
                        "SAFETY_REFUSAL" -> {
                            val msg = obj.optString("reason", obj.optString("answer", trimmed))
                            GeminiStructuredOutput(
                                type = GeminiResponseType.SAFETY_REFUSAL,
                                answer = msg,
                                rawResponse = trimmed
                            )
                        }
                        else -> {
                            // "CONVERSATION" or default
                            val ans = obj.optString("answer", obj.optString("response", trimmed))
                            GeminiStructuredOutput(
                                type = GeminiResponseType.CONVERSATION,
                                answer = ans,
                                rawResponse = trimmed
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Fall back to treating as conversational text
                }
            }

            // Fallback: If not JSON or failed parsing, treat the text as natural language conversation
            return GeminiStructuredOutput(
                type = GeminiResponseType.CONVERSATION,
                answer = trimmed,
                rawResponse = trimmed
            )
        }
    }
}

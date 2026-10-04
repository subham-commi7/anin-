package com.example.core.assistant

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.core.model.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class GeminiDiagnosticsState(
    val isConfigured: Boolean = false,
    val isKeyMaskedPresent: Boolean = false,
    val lastStatusCode: Int? = null,
    val lastLatencyMs: Long = 0L,
    val lastErrorMessage: String? = null,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val modelName: String = "gemini-2.5-flash"
)

sealed class GeminiApiResult {
    data class Success(val structuredOutput: GeminiStructuredOutput, val latencyMs: Long) : GeminiApiResult()
    data class Failure(val statusCode: Int?, val message: String, val isNetworkOrTimeout: Boolean = false) : GeminiApiResult()
}

class GeminiApiClient(
    private val context: Context,
    private val capabilityRegistry: CapabilityRegistry
) {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _diagnostics = MutableStateFlow(
        GeminiDiagnosticsState(
            isConfigured = isKeyConfigured(),
            isKeyMaskedPresent = isKeyConfigured(),
            modelName = MODEL_NAME
        )
    )
    val diagnostics: StateFlow<GeminiDiagnosticsState> = _diagnostics.asStateFlow()

    fun isKeyConfigured(): Boolean {
        val key = getApiKey()
        return !key.isNullOrBlank() && key != "YOUR_GEMINI_API_KEY" && key != "MY_GEMINI_API_KEY_DEFAULT_VALUE"
    }

    private fun getApiKey(): String? {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateHybridResponse(
        userQuery: String,
        language: VoiceLanguage,
        assistantContext: AssistantContext
    ): GeminiApiResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            _diagnostics.value = _diagnostics.value.copy(
                isConfigured = false,
                isKeyMaskedPresent = false,
                lastErrorMessage = "GEMINI_API_KEY not configured in .env / AI Studio Secrets panel."
            )
            return@withContext GeminiApiResult.Failure(
                statusCode = null,
                message = "Gemini API key is not configured. Please configure GEMINI_API_KEY in AI Studio Secrets panel."
            )
        }

        val startTime = System.currentTimeMillis()
        val capabilitiesDesc = capabilityRegistry.getCapabilitiesSummaryForAi()
        val contextPrompt = assistantContext.toGeminiContextPrompt()

        val systemInstructionText = """
            You are Anin, Subham's private, trusted, highly capable personal Android voice assistant on his iQOO Neo 10R.
            User: Subham Sarkar.
            Current Language: ${language.displayName} (${language.code}). Always respond naturally in the user's spoken language unless instructed otherwise.

            $capabilitiesDesc

            $contextPrompt

            CRITICAL RULES:
            1. You DO NOT directly execute Android APIs. You propose structured actions, and Anin's local Safety Gate and Action Executor validate and execute them.
            2. Never propose actions for capabilities marked UNAVAILABLE.
            3. Strict Financial Refusal: Any money transfer, UPI, or banking transaction MUST be rejected with type 'SAFETY_REFUSAL'.
            4. Output MUST be valid JSON conforming to one of these formats:
               - For single device action:
                 {"type": "ACTION", "capability": "youtube|battery|maps|camera|settings|browser|reminders|phone_dialer", "intent": "OPEN_APP|BATTERY_STATUS|REMINDER_CREATE|...", "arguments": {"query": "..."}, "response": "Short natural response to Subham"}
               - For multi-step actions (e.g. open YouTube AND set reminder):
                 {"type": "MULTI_ACTION", "actions": [{"capability": "youtube", "intent": "OPEN_APP", "arguments": {}}, {"capability": "reminders", "intent": "REMINDER_CREATE", "arguments": {"title": "Study", "time": "1 hour"}}], "response": "Short natural response"}
               - For general conversation, explanation, advice, routine, educational help:
                 {"type": "CONVERSATION", "answer": "Detailed helpful answer in ${language.displayName}"}
               - For clarification:
                 {"type": "CLARIFICATION", "question": "Question to Subham"}
        """.trimIndent()

        val requestJson = JSONObject().apply {
            // System instruction
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            })

            // Contents
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userQuery) })
                    })
                })
            })

            // Generation config with JSON response schema
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)
        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        _diagnostics.value = _diagnostics.value.copy(
            totalRequests = _diagnostics.value.totalRequests + 1
        )

        try {
            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = when (statusCode) {
                    400 -> "HTTP 400 Bad Request: Check API parameters or model format."
                    401 -> "HTTP 401 Unauthorized: Invalid or missing Gemini API key. Please check AI Studio Secrets panel."
                    403 -> "HTTP 403 Forbidden: Key lacks permission for $MODEL_NAME or region blocked."
                    429 -> "HTTP 429 Quota Exceeded / Rate Limit: Gemini request limit reached."
                    500, 503 -> "HTTP $statusCode Server Error: Gemini backend temporarily unreachable."
                    else -> "HTTP $statusCode: $responseBody"
                }

                _diagnostics.value = _diagnostics.value.copy(
                    lastStatusCode = statusCode,
                    lastLatencyMs = latency,
                    lastErrorMessage = errorMsg,
                    failedRequests = _diagnostics.value.failedRequests + 1
                )

                return@withContext GeminiApiResult.Failure(
                    statusCode = statusCode,
                    message = errorMsg
                )
            }

            // Parse response
            val respObj = JSONObject(responseBody)
            val candidates = respObj.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val rawOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

            val structured = GeminiStructuredOutput.parse(rawOutput)

            _diagnostics.value = _diagnostics.value.copy(
                lastStatusCode = 200,
                lastLatencyMs = latency,
                lastErrorMessage = null,
                successfulRequests = _diagnostics.value.successfulRequests + 1
            )

            GeminiApiResult.Success(
                structuredOutput = structured,
                latencyMs = latency
            )
        } catch (e: SocketTimeoutException) {
            val latency = System.currentTimeMillis() - startTime
            val msg = "Gemini request timed out after ${latency}ms."
            _diagnostics.value = _diagnostics.value.copy(
                lastStatusCode = null,
                lastLatencyMs = latency,
                lastErrorMessage = msg,
                failedRequests = _diagnostics.value.failedRequests + 1
            )
            GeminiApiResult.Failure(statusCode = null, message = msg, isNetworkOrTimeout = true)
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            val msg = "Network connection failed while calling Gemini: ${e.message}"
            _diagnostics.value = _diagnostics.value.copy(
                lastStatusCode = null,
                lastLatencyMs = latency,
                lastErrorMessage = msg,
                failedRequests = _diagnostics.value.failedRequests + 1
            )
            GeminiApiResult.Failure(statusCode = null, message = msg, isNetworkOrTimeout = true)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val msg = "Gemini execution exception: ${e.message}"
            _diagnostics.value = _diagnostics.value.copy(
                lastStatusCode = null,
                lastLatencyMs = latency,
                lastErrorMessage = msg,
                failedRequests = _diagnostics.value.failedRequests + 1
            )
            GeminiApiResult.Failure(statusCode = null, message = msg)
        }
    }
}

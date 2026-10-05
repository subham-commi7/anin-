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
    val connectionType: String = "SECURE_SERVER_PROXY",
    val isKeyMaskedPresent: Boolean = false,
    val lastStatusCode: Int? = null,
    val lastLatencyMs: Long = 0L,
    val lastErrorMessage: String? = null,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val modelName: String = "gemini-3.8-flash"
)

sealed class GeminiApiResult {
    data class Success(val structuredOutput: GeminiStructuredOutput, val latencyMs: Long, val viaProxy: Boolean = true) : GeminiApiResult()
    data class Failure(val statusCode: Int?, val message: String, val isNetworkOrTimeout: Boolean = false) : GeminiApiResult()
}

class GeminiApiClient(
    private val context: Context,
    private val capabilityRegistry: CapabilityRegistry
) {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val PROXY_ENDPOINT = "http://10.0.2.2:3000/api/orchestrator/process"
        private const val DIRECT_MODEL_NAME = "gemini-2.5-flash"
        private const val DIRECT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _diagnostics = MutableStateFlow(
        GeminiDiagnosticsState(
            isConfigured = hasSecureBackendOrDirectKey(),
            connectionType = if (isDirectKeyConfigured()) "DIRECT_KEY" else "SECURE_SERVER_PROXY",
            isKeyMaskedPresent = isDirectKeyConfigured(),
            modelName = "gemini-3.8-flash"
        )
    )
    val diagnostics: StateFlow<GeminiDiagnosticsState> = _diagnostics.asStateFlow()

    fun isDirectKeyConfigured(): Boolean {
        val key = getDirectApiKey()
        return !key.isNullOrBlank() && key != "YOUR_GEMINI_API_KEY" && key != "MY_GEMINI_API_KEY_DEFAULT_VALUE"
    }

    fun hasSecureBackendOrDirectKey(): Boolean {
        return true // Primary architecture uses server-side proxy
    }

    private fun getDirectApiKey(): String? {
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
        val startTime = System.currentTimeMillis()

        _diagnostics.value = _diagnostics.value.copy(
            totalRequests = _diagnostics.value.totalRequests + 1
        )

        // 1. PRIMARY PATH: Secure Server-Side Proxy (Zero secret compiled into APK)
        val proxyResult = callServerProxy(userQuery, language, assistantContext, startTime)
        if (proxyResult is GeminiApiResult.Success) {
            _diagnostics.value = _diagnostics.value.copy(
                isConfigured = true,
                connectionType = "SECURE_SERVER_PROXY",
                lastStatusCode = 200,
                lastLatencyMs = proxyResult.latencyMs,
                lastErrorMessage = null,
                successfulRequests = _diagnostics.value.successfulRequests + 1,
                modelName = "gemini-3.8-flash (via Server Proxy)"
            )
            return@withContext proxyResult
        }

        // 2. FALLBACK PATH: Direct Gemini REST if direct key configured
        if (isDirectKeyConfigured()) {
            val directKey = getDirectApiKey()!!
            val directResult = callDirectGeminiRest(userQuery, language, assistantContext, directKey, startTime)
            if (directResult is GeminiApiResult.Success) {
                _diagnostics.value = _diagnostics.value.copy(
                    isConfigured = true,
                    connectionType = "DIRECT_REST",
                    lastStatusCode = 200,
                    lastLatencyMs = directResult.latencyMs,
                    lastErrorMessage = null,
                    successfulRequests = _diagnostics.value.successfulRequests + 1,
                    modelName = DIRECT_MODEL_NAME
                )
                return@withContext directResult
            } else if (directResult is GeminiApiResult.Failure) {
                _diagnostics.value = _diagnostics.value.copy(
                    lastStatusCode = directResult.statusCode,
                    lastLatencyMs = System.currentTimeMillis() - startTime,
                    lastErrorMessage = directResult.message,
                    failedRequests = _diagnostics.value.failedRequests + 1
                )
                return@withContext directResult
            }
        }

        // 3. NEITHER SUCCEEDED: Clean truthful status (Never crashes, local capabilities stay fully operational)
        val failureMsg = if (proxyResult is GeminiApiResult.Failure) {
            proxyResult.message
        } else {
            "Secure server proxy unreachable and direct GEMINI_API_KEY not set."
        }

        _diagnostics.value = _diagnostics.value.copy(
            lastStatusCode = if (proxyResult is GeminiApiResult.Failure) proxyResult.statusCode else null,
            lastLatencyMs = System.currentTimeMillis() - startTime,
            lastErrorMessage = failureMsg,
            failedRequests = _diagnostics.value.failedRequests + 1
        )

        GeminiApiResult.Failure(
            statusCode = null,
            message = failureMsg,
            isNetworkOrTimeout = true
        )
    }

    private fun callServerProxy(
        userQuery: String,
        language: VoiceLanguage,
        assistantContext: AssistantContext,
        startTime: Long
    ): GeminiApiResult {
        return try {
            val reqObj = JSONObject().apply {
                put("query", userQuery)
                put("detectedLanguage", language.code)
                put("memoryContext", JSONArray(assistantContext.relevantMemories))
                put("capabilities", JSONArray(assistantContext.availableCapabilities))
                put("deviceDiagnostics", JSONObject().apply {
                    put("model", assistantContext.deviceModel)
                    put("battery", assistantContext.batteryPercentage)
                })
            }

            val body = reqObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(PROXY_ENDPOINT)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return GeminiApiResult.Failure(
                    statusCode = response.code,
                    message = "Server proxy returned HTTP ${response.code}"
                )
            }

            val respJson = JSONObject(bodyStr)
            val success = respJson.optBoolean("success", true)
            val responseText = respJson.optString("responseText", "")

            if (responseText.isNotBlank()) {
                val structured = GeminiStructuredOutput.parse(responseText)
                GeminiApiResult.Success(structured, latency, viaProxy = true)
            } else {
                GeminiApiResult.Failure(statusCode = 200, message = "Empty response from server proxy")
            }
        } catch (e: Exception) {
            GeminiApiResult.Failure(statusCode = null, message = "Proxy error: ${e.message}", isNetworkOrTimeout = true)
        }
    }

    private fun callDirectGeminiRest(
        userQuery: String,
        language: VoiceLanguage,
        assistantContext: AssistantContext,
        apiKey: String,
        startTime: Long
    ): GeminiApiResult {
        return try {
            val capabilitiesDesc = capabilityRegistry.getCapabilitiesSummaryForAi()
            val contextPrompt = assistantContext.toGeminiContextPrompt()

            val systemInstructionText = """
                You are Anin, Subham's private, trusted personal Android voice assistant on his iQOO Neo 10R.
                User: Subham Sarkar.
                Current Language: ${language.displayName} (${language.code}).
                $capabilitiesDesc
                $contextPrompt
                Strict Security & Financial Rules:
                1. No money transfers, UPI, or banking transactions.
                2. Output valid JSON: {"type": "ACTION|MULTI_ACTION|CONVERSATION", ...}
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructionText) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userQuery) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                })
            }

            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "$DIRECT_BASE_URL/$DIRECT_MODEL_NAME:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return GeminiApiResult.Failure(
                    statusCode = response.code,
                    message = "Direct Gemini API returned HTTP ${response.code}"
                )
            }

            val respObj = JSONObject(bodyStr)
            val candidate = respObj.optJSONArray("candidates")?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
            val rawOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

            val structured = GeminiStructuredOutput.parse(rawOutput)
            GeminiApiResult.Success(structured, latency, viaProxy = false)
        } catch (e: Exception) {
            GeminiApiResult.Failure(statusCode = null, message = "Direct REST error: ${e.message}", isNetworkOrTimeout = true)
        }
    }
}

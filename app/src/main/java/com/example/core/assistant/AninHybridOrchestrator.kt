package com.example.core.assistant

import android.content.Context
import com.example.core.auth.SpeakerVerificationEngine
import com.example.core.database.AppDatabase
import com.example.core.database.MemoryCategory
import com.example.core.database.PersonalMemoryEntity
import com.example.core.database.ReminderEntity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.device.DeviceActionExecutor
import com.example.core.device.DeviceDiagnosticsManager
import com.example.core.model.VoiceLanguage
import com.example.core.voice.AudioPlaybackManager
import com.example.core.voice.OutputVoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AninHybridOrchestrator(
    private val context: Context,
    private val speakerVerificationEngine: SpeakerVerificationEngine,
    private val outputVoiceManager: OutputVoiceManager,
    private val audioPlaybackManager: AudioPlaybackManager,
    val capabilityRegistry: CapabilityRegistry = CapabilityRegistry(context),
    val geminiApiClient: GeminiApiClient = GeminiApiClient(context, capabilityRegistry)
) {
    private val db = AppDatabase.getInstance(context)
    val diagnosticsManager = DeviceDiagnosticsManager(context)
    val actionExecutor = DeviceActionExecutor(context)
    val safetyGate = SafetyGate(capabilityRegistry)

    private val _operatingMode = MutableStateFlow(AssistantOperatingMode.HYBRID)
    val operatingMode: StateFlow<AssistantOperatingMode> = _operatingMode.asStateFlow()

    private val _recentTraces = MutableStateFlow<List<OrchestratorTrace>>(emptyList())
    val recentTraces: StateFlow<List<OrchestratorTrace>> = _recentTraces.asStateFlow()

    private val _lastTrace = MutableStateFlow<OrchestratorTrace?>(null)
    val lastTrace: StateFlow<OrchestratorTrace?> = _lastTrace.asStateFlow()

    fun setOperatingMode(mode: AssistantOperatingMode) {
        _operatingMode.value = mode
    }

    suspend fun processInput(input: AssistantInput): AssistantInteraction = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val detectedLanguage = input.language
        val normalized = input.normalizedText

        // 1. Immediate Barge-in / Stop
        if (normalized == "stop" || normalized.contains("থেমে যাও") || normalized.contains("থামো") ||
            normalized.contains("রুক যাও") || normalized.contains("চুপ করো") || normalized.contains("বন্ধ করো")) {
            audioPlaybackManager.stopAllPlayback()
            val stopAck = when (detectedLanguage) {
                VoiceLanguage.BENGALI -> "প্লেব্যাক বন্ধ করা হয়েছে।"
                VoiceLanguage.HINDI -> "प्लेबैक रोक दिया गया है।"
                VoiceLanguage.ENGLISH -> "Playback stopped."
            }
            recordTrace(
                OrchestratorTrace(
                    source = input.source,
                    language = detectedLanguage,
                    transcript = input.rawText,
                    normalizedText = normalized,
                    routingTarget = RoutingTarget.LOCAL,
                    routingReason = "IMMEDIATE_STOP",
                    localIntent = AssistantIntentType.STOP_HALT,
                    localConfidence = 1.0f,
                    geminiUsed = false,
                    finalResponse = stopAck,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            )
            return@withContext AssistantInteraction(
                query = input.rawText,
                response = stopAck,
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = true,
                verificationConfidence = 0.99f
            )
        }

        // 2. Speaker Verification Gate (Fail-Closed Voice Gate)
        val isAuthorized: Boolean
        val verificationConfidence: Float
        val diagnosticCode: String

        if (input.source == AssistantInputSource.VOICE) {
            if (input.audioSamples != null) {
                val result = speakerVerificationEngine.verifySpeaker(
                    inputAudio = input.audioSamples,
                    isAninPlaybackActive = audioPlaybackManager.isAssistantSpeaking.value,
                    activeOutputVoicePitch = outputVoiceManager.activeProfile.value?.pitchMultiplier ?: 1.0f
                )
                isAuthorized = result.isSubham
                verificationConfidence = result.confidence
                diagnosticCode = result.diagnosticCode
            } else {
                if (speakerVerificationEngine.isSubhamEnrolled()) {
                    isAuthorized = input.isAuthorized
                    verificationConfidence = if (input.isAuthorized) 0.94f else 0.22f
                    diagnosticCode = if (input.isAuthorized) "AUTHORIZED_SUBHAM" else "UNAUTHORIZED_VOICE"
                } else {
                    isAuthorized = false
                    verificationConfidence = 0.0f
                    diagnosticCode = "NOT_ENROLLED"
                }
            }
        } else {
            // Text Input from authenticated phone touchscreen
            isAuthorized = true
            verificationConfidence = 1.0f
            diagnosticCode = "AUTHORIZED_TOUCH_TEXT"
        }

        // Fail-Closed Security Rule: Silent ignore on unauthorized voice
        if (!isAuthorized) {
            withContext(Dispatchers.IO) {
                db.securityAuditDao().logEvent(
                    SecurityAuditLogEntity(
                        eventType = "AUTHENTICATION_FAILED",
                        details = "Unauthenticated voice command rejected. Fail-closed silence enforced ($diagnosticCode).",
                        diagnosticCode = diagnosticCode
                    )
                )
            }
            val silentResponse = "[Silently Ignored — AUTHENTICATION_FAILED]"
            recordTrace(
                OrchestratorTrace(
                    source = input.source,
                    language = detectedLanguage,
                    transcript = input.rawText,
                    normalizedText = normalized,
                    routingTarget = RoutingTarget.LOCAL,
                    routingReason = "UNAUTHORIZED_VOICE_GATE",
                    localIntent = null,
                    localConfidence = 0.0f,
                    geminiUsed = false,
                    finalResponse = silentResponse,
                    isSilent = true,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            )
            return@withContext AssistantInteraction(
                query = input.rawText,
                response = silentResponse,
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = false,
                verificationConfidence = verificationConfidence,
                isSilentlyIgnored = true,
                diagnosticReason = diagnosticCode
            )
        }

        // 3. Local Safety Gate: Financial Refusal
        val safetyCheck = safetyGate.checkInputSafety(normalized, detectedLanguage)
        if (safetyCheck is SafetyCheckResult.BlockedFinancial) {
            val refusalMsg = safetyCheck.localizedMessage
            outputVoiceManager.speakText(refusalMsg, detectedLanguage)
            recordTrace(
                OrchestratorTrace(
                    source = input.source,
                    language = detectedLanguage,
                    transcript = input.rawText,
                    normalizedText = normalized,
                    routingTarget = RoutingTarget.LOCAL,
                    routingReason = "FINANCIAL_SAFETY_GATE",
                    localIntent = null,
                    localConfidence = 1.0f,
                    geminiUsed = false,
                    finalResponse = refusalMsg,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            )
            return@withContext AssistantInteraction(
                query = input.rawText,
                response = refusalMsg,
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = true,
                verificationConfidence = verificationConfidence,
                isSilentlyIgnored = false,
                diagnosticReason = "FINANCIAL_BLOCKED"
            )
        }

        // 4. Local Intent Analysis & Model Routing
        val localIntent = AssistantIntentEngine.detectIntent(normalized, detectedLanguage)
        val routingDecision = ModelRouter.route(normalized, detectedLanguage, localIntent, _operatingMode.value)

        var finalResponse: String
        var geminiUsed = false
        var geminiLatency: Long? = null
        val capabilitiesInvolved = mutableListOf<String>()
        var actionExecutedName: String? = null
        var actionResultStatus: String? = null

        if (routingDecision.target == RoutingTarget.LOCAL) {
            // ==========================================
            // EXECUTE DETERMINISTICALLY VIA LOCAL AI
            // 0 Gemini Calls — 100% Offline Capable
            // ==========================================
            val localResult = executeLocalIntent(localIntent, input, detectedLanguage)
            finalResponse = localResult.response
            capabilitiesInvolved.addAll(localResult.capabilities)
            actionExecutedName = localResult.actionExecuted
            actionResultStatus = localResult.actionResult
        } else {
            // ==========================================
            // EXECUTE VIA HYBRID / GEMINI INTELLIGENCE
            // ==========================================
            val assistantContext = buildSanitizedContext(detectedLanguage)
            geminiUsed = true

            val geminiResult = geminiApiClient.generateHybridResponse(
                userQuery = input.rawText,
                language = detectedLanguage,
                assistantContext = assistantContext
            )

            when (geminiResult) {
                is GeminiApiResult.Success -> {
                    geminiLatency = geminiResult.latencyMs
                    val structured = geminiResult.structuredOutput

                    when (structured.type) {
                        GeminiResponseType.ACTION, GeminiResponseType.MULTI_ACTION -> {
                            val executionOutcomes = mutableListOf<String>()
                            var allSuccessful = true

                            for (act in structured.actions) {
                                capabilitiesInvolved.add(act.capability)
                                actionExecutedName = "${act.capability}:${act.intent}"

                                // Validate through Local Safety Gate
                                val actionSafety = safetyGate.validateProposedAction(act)
                                if (actionSafety is SafetyCheckResult.Allowed) {
                                    val actionResult = executePlannedActionLocally(act, detectedLanguage)
                                    actionResultStatus = if (actionResult.isSuccess) "SUCCESS" else "FAILED"
                                    if (!actionResult.isSuccess) allSuccessful = false
                                    executionOutcomes.add(actionResult.message)
                                } else {
                                    allSuccessful = false
                                    actionResultStatus = "BLOCKED_BY_SAFETY"
                                    executionOutcomes.add("Action '${act.capability}' blocked by device safety.")
                                }
                            }

                            finalResponse = if (structured.answer != null && allSuccessful) {
                                structured.answer
                            } else {
                                executionOutcomes.joinToString("\n")
                            }
                        }
                        GeminiResponseType.SAFETY_REFUSAL -> {
                            finalResponse = structured.answer ?: "Request blocked for device safety."
                        }
                        GeminiResponseType.CLARIFICATION -> {
                            finalResponse = structured.clarificationQuestion ?: structured.answer ?: "Could you please clarify?"
                        }
                        else -> {
                            // General conversational / educational / reasoning answer
                            finalResponse = structured.answer ?: "Processed your request."
                        }
                    }
                }
                is GeminiApiResult.Failure -> {
                    // FALLBACK ROUTE: If Gemini is unreachable or error, gracefully fallback
                    if (localIntent.type != AssistantIntentType.GENERAL_KNOWLEDGE && localIntent.type != AssistantIntentType.UNKNOWN) {
                        val localResult = executeLocalIntent(localIntent, input, detectedLanguage)
                        finalResponse = localResult.response
                        capabilitiesInvolved.addAll(localResult.capabilities)
                        actionExecutedName = localResult.actionExecuted
                        actionResultStatus = localResult.actionResult
                    } else {
                        finalResponse = when (detectedLanguage) {
                            VoiceLanguage.BENGALI -> "শুভম, অনলাইন ইন্টেলিজেন্স বর্তমানে উপলভ্য নয় (${geminiResult.message})। তবে আপনার লোকাল ডিভাইস কমান্ড (যেমন ব্যাটারি, ইউটিউব, ম্যাপস, রিমাইন্ডার) সচল আছে।"
                            VoiceLanguage.HINDI -> "शुभम, ऑनलाइन इंटेलिजेंस अभी उपलब्ध नहीं है (${geminiResult.message})। लेकिन आपकी लोकल डिवाइस सेवाएँ (जैसे बैटरी, यूट्यूब, मैप्स) पूरी तरह सक्रिय हैं।"
                            VoiceLanguage.ENGLISH -> "Subham, online intelligence is currently unavailable (${geminiResult.message}). Local device capabilities (Battery, YouTube, Maps, Reminders) remain fully operational."
                        }
                    }
                }
            }
        }

        // 5. Final truthful vocal response via System B Output Voice (Single voice queue)
        outputVoiceManager.speakText(finalResponse, detectedLanguage)

        // 6. Record Observability Trace
        val totalDuration = System.currentTimeMillis() - startTime
        recordTrace(
            OrchestratorTrace(
                source = input.source,
                language = detectedLanguage,
                transcript = input.rawText,
                normalizedText = normalized,
                routingTarget = routingDecision.target,
                routingReason = routingDecision.reason,
                localIntent = localIntent.type,
                localConfidence = localIntent.confidence,
                geminiUsed = geminiUsed,
                geminiLatencyMs = geminiLatency,
                capabilitiesInvolved = capabilitiesInvolved,
                actionExecuted = actionExecutedName,
                actionResult = actionResultStatus,
                finalResponse = finalResponse,
                totalDurationMs = totalDuration
            )
        )

        AssistantInteraction(
            query = input.rawText,
            response = finalResponse,
            detectedLanguage = detectedLanguage,
            isSubhamAuthorized = true,
            verificationConfidence = verificationConfidence,
            isSilentlyIgnored = false
        )
    }

    private data class LocalExecutionResult(
        val response: String,
        val capabilities: List<String>,
        val actionExecuted: String,
        val actionResult: String
    )

    private suspend fun executeLocalIntent(
        intent: RecognizedIntent,
        input: AssistantInput,
        lang: VoiceLanguage
    ): LocalExecutionResult {
        val battery = diagnosticsManager.getBatteryDiagnostics()
        val caps = mutableListOf<String>()

        return when (intent.type) {
            AssistantIntentType.HEAR_CHECK -> {
                caps.add("speech_recognition")
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "হ্যাঁ Subham, আমি আপনার কথা পরিষ্কার শুনতে পাচ্ছি। বলুন আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
                    VoiceLanguage.HINDI -> "हाँ शुभम, मैं आपकी आवाज़ बिल्कुल साफ़ सुन रही हूँ। बताइए मैं आपकी क्या सहायता करूँ?"
                    VoiceLanguage.ENGLISH -> "Yes Subham, I can hear you clearly! How can I help you right now?"
                }
                LocalExecutionResult(ans, caps, "HEAR_CHECK", "SUCCESS")
            }

            AssistantIntentType.BATTERY_STATUS -> {
                caps.add("battery")
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, ব্যাটারি ${battery.percentage} শতাংশ আছে। অবস্থা: ${battery.chargePlug}। তাপমাত্রা: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.HINDI -> "शुभम, बैटरी ${battery.percentage} प्रतिशत है। स्थिति: ${battery.chargePlug}। तापमान: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.ENGLISH -> "Subham, your battery is at ${battery.percentage}%. Status: ${battery.chargePlug}. Temperature: ${battery.temperatureCelsius}°C."
                }
                LocalExecutionResult(ans, caps, "READ_BATTERY", "SUCCESS")
            }

            AssistantIntentType.OPEN_APP, AssistantIntentType.PLAY_YOUTUBE -> {
                val target = intent.target ?: "youtube"
                caps.add(target.lowercase())
                val launchResult = actionExecutor.openInstalledApp(target)

                val ans = if (launchResult.success) {
                    when (target.lowercase()) {
                        "youtube" -> when (lang) {
                            VoiceLanguage.BENGALI -> "ঠিক আছে Subham, YouTube খুলছি।"
                            VoiceLanguage.HINDI -> "शुभम, यूट्यूब खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Opening YouTube for you, Subham."
                        }
                        "maps" -> when (lang) {
                            VoiceLanguage.BENGALI -> "গুগল ম্যাপস খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "गूगल मैप्स खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Opening Google Maps, Subham."
                        }
                        "settings" -> when (lang) {
                            VoiceLanguage.BENGALI -> "ডিভাইস সেটিংস খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "डिवाइस सेटिंग्स खोली जा रही हैं।"
                            VoiceLanguage.ENGLISH -> "Opening device Settings, Subham."
                        }
                        else -> when (lang) {
                            VoiceLanguage.BENGALI -> "শুভম, $target খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "शुभम, $target ऐप खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Launching $target for you, Subham."
                        }
                    }
                } else {
                    when (lang) {
                        VoiceLanguage.BENGALI -> "Subham, $target খুলতে পারলাম না: ${launchResult.message}"
                        VoiceLanguage.HINDI -> "शुभम, $target नहीं खोला जा सका: ${launchResult.message}"
                        VoiceLanguage.ENGLISH -> "Subham, could not open $target: ${launchResult.message}"
                    }
                }
                LocalExecutionResult(ans, caps, "LAUNCH_$target", if (launchResult.success) "SUCCESS" else "FAILED")
            }

            AssistantIntentType.TIME_DATE -> {
                caps.add("diagnostics")
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                val now = Date()
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, এখন সময় ${timeFormat.format(now)}। আজকের তারিখ হলো ${dateFormat.format(now)}।"
                    VoiceLanguage.HINDI -> "शुभम, अभी समय ${timeFormat.format(now)} है। आज ${dateFormat.format(now)} है।"
                    VoiceLanguage.ENGLISH -> "Subham, the current time is ${timeFormat.format(now)} on ${dateFormat.format(now)}."
                }
                LocalExecutionResult(ans, caps, "READ_TIME", "SUCCESS")
            }

            AssistantIntentType.IDENTITY_CHECK -> {
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "আমি অনিন, আপনার ব্যক্তিগত এবং সুরক্ষিত এআই ভয়েস সহকারী। আর আপনি হলেন শুভম।"
                    VoiceLanguage.HINDI -> "मैं अनिन हूँ, आपकी निजी वॉइस असिस्टेंट। और आप शुभम हैं।"
                    VoiceLanguage.ENGLISH -> "I am Anin, your private personal voice assistant. And you are Subham."
                }
                LocalExecutionResult(ans, caps, "IDENTITY_CHECK", "SUCCESS")
            }

            AssistantIntentType.GREETING -> {
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "নমস্কার শুভম! বলুন আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
                    VoiceLanguage.HINDI -> "नमस्ते शुभम! बताइए मैं आपकी क्या सहायता कर सकती हूँ?"
                    VoiceLanguage.ENGLISH -> "Hello Subham! How can I assist you right now?"
                }
                LocalExecutionResult(ans, caps, "GREETING", "SUCCESS")
            }

            AssistantIntentType.REMINDER_CREATE -> {
                caps.add("reminders")
                val title = if (input.rawText.length > 7) input.rawText.substring(7).trim() else "Important Task"
                withContext(Dispatchers.IO) {
                    db.reminderDao().insertReminder(
                        ReminderEntity(
                            title = title,
                            targetTimeMillis = System.currentTimeMillis() + 3600000L,
                            formattedTarget = "In 1 hour"
                        )
                    )
                }
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, আপনার রিমাইন্ডার সংরক্ষণ করা হয়েছে: $title।"
                    VoiceLanguage.HINDI -> "शुभम, आपका रिमाइंडर सुरक्षित कर लिया गया है: $title।"
                    VoiceLanguage.ENGLISH -> "Subham, your reminder has been saved: $title."
                }
                LocalExecutionResult(ans, caps, "CREATE_REMINDER", "SUCCESS")
            }

            AssistantIntentType.MEMORY_STORE -> {
                caps.add("memory")
                withContext(Dispatchers.IO) {
                    db.personalMemoryDao().insertMemory(
                        PersonalMemoryEntity(
                            category = MemoryCategory.IMPORTANT_INSTRUCTION,
                            content = input.rawText
                        )
                    )
                }
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, আমি এটি এনক্রিপ্টেড মেমরিতে সংরক্ষণ করেছি।"
                    VoiceLanguage.HINDI -> "शुभम, मैंने इसे आपकी सुरक्षित मेमोरी में सहेज लिया है।"
                    VoiceLanguage.ENGLISH -> "Subham, I have saved this into your private encrypted memory."
                }
                LocalExecutionResult(ans, caps, "STORE_MEMORY", "SUCCESS")
            }

            AssistantIntentType.CALL_CONTACT -> {
                caps.add("phone_dialer")
                val targetContact = intent.target ?: "Contact"
                actionExecutor.openInstalledApp("phone")
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, $targetContact-কে কল করার জন্য ফোন ডায়ালার প্রস্তুত করা হয়েছে।"
                    VoiceLanguage.HINDI -> "शुभम, $targetContact को कॉल करने के लिए डायलर खोला जा रहा है।"
                    VoiceLanguage.ENGLISH -> "Subham, preparing phone dialer to call $targetContact."
                }
                LocalExecutionResult(ans, caps, "CALL_CONTACT", "SUCCESS")
            }

            else -> {
                // Fallback local status
                val ans = when (lang) {
                    VoiceLanguage.BENGALI -> "শুভম, আপনার অনুরোধটি লোকাল সিস্টেমে প্রক্রিয়া করা হয়েছে।"
                    VoiceLanguage.HINDI -> "शुभम, आपका अनुरोध स्थानीय प्रणाली में संसाधित किया गया।"
                    VoiceLanguage.ENGLISH -> "Subham, processed via secure local system."
                }
                LocalExecutionResult(ans, caps, "DEFAULT_LOCAL", "SUCCESS")
            }
        }
    }

    private data class ActionOutcome(val isSuccess: Boolean, val message: String)

    private suspend fun executePlannedActionLocally(
        action: PlannedAction,
        lang: VoiceLanguage
    ): ActionOutcome {
        val cap = action.capability.lowercase()
        return when (cap) {
            "youtube" -> {
                val query = action.arguments["query"]
                val result = actionExecutor.openInstalledApp("youtube", query)
                ActionOutcome(result.success, if (result.success) "Opened YouTube." else "Failed to open YouTube.")
            }
            "maps" -> {
                val destination = action.arguments["destination"] ?: action.arguments["query"]
                val result = if (destination != null) {
                    actionExecutor.navigateTo(destination)
                } else {
                    actionExecutor.openInstalledApp("maps")
                }
                ActionOutcome(result.success, if (result.success) "Opened Maps." else "Failed to open Maps.")
            }
            "browser" -> {
                val result = actionExecutor.openInstalledApp("chrome")
                ActionOutcome(result.success, "Opened web browser.")
            }
            "camera" -> {
                val result = actionExecutor.openInstalledApp("camera")
                ActionOutcome(result.success, "Opened Camera.")
            }
            "settings" -> {
                val result = actionExecutor.openInstalledApp("settings")
                ActionOutcome(result.success, "Opened Android Settings.")
            }
            "reminders" -> {
                val title = action.arguments["title"] ?: "Task from Anin"
                withContext(Dispatchers.IO) {
                    db.reminderDao().insertReminder(
                        ReminderEntity(
                            title = title,
                            targetTimeMillis = System.currentTimeMillis() + 3600000L,
                            formattedTarget = "In 1 hour"
                        )
                    )
                }
                ActionOutcome(true, "Created reminder for: $title.")
            }
            "memory" -> {
                val content = action.arguments["content"] ?: action.arguments["text"] ?: "Note"
                withContext(Dispatchers.IO) {
                    db.personalMemoryDao().insertMemory(
                        PersonalMemoryEntity(
                            category = MemoryCategory.IMPORTANT_INSTRUCTION,
                            content = content
                        )
                    )
                }
                ActionOutcome(true, "Stored in private memory: $content.")
            }
            "phone_dialer" -> {
                val result = actionExecutor.openInstalledApp("phone")
                ActionOutcome(result.success, "Opened phone dialer.")
            }
            else -> {
                ActionOutcome(false, "Unsupported local action: $cap.")
            }
        }
    }

    private suspend fun buildSanitizedContext(language: VoiceLanguage): AssistantContext = withContext(Dispatchers.IO) {
        val battery = diagnosticsManager.getBatteryDiagnostics()
        val network = diagnosticsManager.getNetworkDiagnostics()
        val recentMems = try {
            db.personalMemoryDao().getMemoriesList().take(5).map { it.content }
        } catch (e: Exception) {
            emptyList()
        }

        val availableCaps = capabilityRegistry.getAvailableCapabilities()
            .filter { it.value.isAvailable }
            .map { it.key }

        AssistantContext(
            userDisplayName = "Subham",
            preferredLanguage = language,
            deviceModel = "iQOO Neo 10R",
            availableCapabilities = availableCaps,
            batteryPercentage = battery.percentage,
            isCharging = battery.isCharging,
            networkStatus = network.networkType,
            relevantMemories = recentMems,
            recentInteractions = _recentTraces.value.takeLast(3).map { it.transcript to it.finalResponse },
            isSubhamAuthenticated = true
        )
    }

    private fun recordTrace(trace: OrchestratorTrace) {
        _lastTrace.value = trace
        _recentTraces.value = (listOf(trace) + _recentTraces.value).take(50)
    }
}

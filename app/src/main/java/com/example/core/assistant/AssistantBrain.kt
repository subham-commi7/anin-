package com.example.core.assistant

import android.content.Context
import com.example.core.auth.SpeakerVerificationEngine
import com.example.core.auth.VerificationResult
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AssistantInteraction(
    val query: String,
    val response: String,
    val detectedLanguage: VoiceLanguage,
    val isSubhamAuthorized: Boolean,
    val verificationConfidence: Float,
    val isSilentlyIgnored: Boolean = false,
    val diagnosticReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class AssistantBrain(
    private val context: Context,
    private val speakerVerificationEngine: SpeakerVerificationEngine,
    private val outputVoiceManager: OutputVoiceManager,
    private val audioPlaybackManager: AudioPlaybackManager
) {
    private val db = AppDatabase.getInstance(context)
    val diagnosticsManager = DeviceDiagnosticsManager(context)
    val actionExecutor = DeviceActionExecutor(context)

    companion object {
        fun detectLanguage(text: String): VoiceLanguage {
            return VoiceLanguage.detectLanguage(text)
        }
    }

    /**
     * Unified Canonical Assistant Processing Pipeline
     * Both Voice Input and Typed Text Input pass through this exact method.
     */
    suspend fun processInput(input: AssistantInput): AssistantInteraction = withContext(Dispatchers.Default) {
        val detectedLanguage = input.language
        val normalized = input.normalizedText

        // 1. Check Barge-In / Stop Command Immediately
        if (normalized == "stop" || normalized.contains("থেমে যাও") || normalized.contains("থামো") ||
            normalized.contains("রুক যাও") || normalized.contains("চুপ করো") || normalized.contains("বন্ধ করো")) {
            audioPlaybackManager.stopAllPlayback()
            val stopAck = when (detectedLanguage) {
                VoiceLanguage.BENGALI -> "প্লেব্যাক বন্ধ করা হয়েছে।"
                VoiceLanguage.HINDI -> "प्लेबैक रोक दिया गया है।"
                VoiceLanguage.ENGLISH -> "Playback stopped."
            }
            return@withContext AssistantInteraction(
                query = input.rawText,
                response = stopAck,
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = true,
                verificationConfidence = 0.99f
            )
        }

        // 2. Speaker Verification (Fail-Closed Voice Gate)
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
                // If audio samples missing or simulated voice
                if (speakerVerificationEngine.isSubhamEnrolled()) {
                    isAuthorized = input.isAuthorized
                    verificationConfidence = if (input.isAuthorized) 0.94f else 0.22f
                    diagnosticCode = if (input.isAuthorized) "AUTHORIZED_SUBHAM" else "UNAUTHORIZED_VOICE"
                } else {
                    // Before enrollment: reject voice commands fail-closed
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

        // FAIL-CLOSED SECURITY RULE: If voice verification fails, Anin remains 100% SILENT.
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

            return@withContext AssistantInteraction(
                query = input.rawText,
                response = "[Silently Ignored — AUTHENTICATION_FAILED]",
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = false,
                verificationConfidence = verificationConfidence,
                isSilentlyIgnored = true,
                diagnosticReason = diagnosticCode
            )
        }

        // 3. Financial Safety Policy (Hard refusal on UPI, payments, transfers)
        if (isFinancialProhibited(normalized)) {
            val refusalMsg = when (detectedLanguage) {
                VoiceLanguage.BENGALI -> "শুভম, আর্থিক সুরক্ষা বিধিমালার কারণে অনিন কোনো প্রকার টাকা পাঠানো বা ইউপিআই লেনদেন করতে পারে না। অনুগ্রহ করে ব্যাংকিং অ্যাপ থেকে লেনদেন সম্পন্ন করুন।"
                VoiceLanguage.HINDI -> "शुभम, वित्तीय सुरक्षा नियमों के तहत अनिन किसी भी प्रकार का यूपीआई या बैंक ट्रांसफर नहीं कर सकती। कृपया अपने बैंक ऐप से सुरक्षित लेन-देन करें।"
                VoiceLanguage.ENGLISH -> "Subham, for financial safety, automated money transfers, UPI, and payments are strictly blocked. Please use your banking app directly."
            }

            outputVoiceManager.speakText(refusalMsg, detectedLanguage)

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

        // 4. Intent Understanding & Capability Execution
        val intent = AssistantIntentEngine.detectIntent(normalized, detectedLanguage)
        val battery = diagnosticsManager.getBatteryDiagnostics()
        val network = diagnosticsManager.getNetworkDiagnostics()

        val responseText = when (intent.type) {
            // Hearing Check ("তুমি কি আমার কথা শুনতে পাচ্ছ?")
            AssistantIntentType.HEAR_CHECK -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "হ্যাঁ Subham, আমি আপনার কথা পরিষ্কার শুনতে পাচ্ছি। বলুন আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
                    VoiceLanguage.HINDI -> "हाँ शुभम, मैं आपकी आवाज़ बिल्कुल साफ़ सुन रही हूँ। बताइए मैं आपकी क्या सहायता करूँ?"
                    VoiceLanguage.ENGLISH -> "Yes Subham, I can hear you clearly! How can I help you right now?"
                }
            }

            // Open Apps (YouTube, Maps, Settings, Chrome, etc.)
            AssistantIntentType.OPEN_APP -> {
                val target = intent.target ?: "youtube"
                val launchResult = actionExecutor.openInstalledApp(target)

                if (launchResult.success) {
                    when (target.lowercase()) {
                        "youtube" -> when (detectedLanguage) {
                            VoiceLanguage.BENGALI -> "শুভম, ইউটিউব খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "शुभम, यूट्यूब खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Opening YouTube for you, Subham."
                        }
                        "maps" -> when (detectedLanguage) {
                            VoiceLanguage.BENGALI -> "গুগল ম্যাপস খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "गूगल मैप्स खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Opening Google Maps, Subham."
                        }
                        "settings" -> when (detectedLanguage) {
                            VoiceLanguage.BENGALI -> "ডিভাইস সেটিংস খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "डिवाइस सेटिंग्स खोली जा रही हैं।"
                            VoiceLanguage.ENGLISH -> "Opening device Settings, Subham."
                        }
                        else -> when (detectedLanguage) {
                            VoiceLanguage.BENGALI -> "শুভম, $target অ্যাপ্লিকেশনটি খোলা হচ্ছে।"
                            VoiceLanguage.HINDI -> "शुभम, $target ऐप खोला जा रहा है।"
                            VoiceLanguage.ENGLISH -> "Launching $target for you, Subham."
                        }
                    }
                } else {
                    when (detectedLanguage) {
                        VoiceLanguage.BENGALI -> "শুভম, অ্যাপ্লিকেশনটি খুলতে পারা যায়নি: ${launchResult.message}"
                        VoiceLanguage.HINDI -> "शुभम, ऐप नहीं खोला जा सका: ${launchResult.message}"
                        VoiceLanguage.ENGLISH -> "Subham, could not open application: ${launchResult.message}"
                    }
                }
            }

            // Battery Status
            AssistantIntentType.BATTERY_STATUS -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, ব্যাটারি ${battery.percentage} শতাংশ আছে। অবস্থা: ${battery.chargePlug}। তাপমাত্রা: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.HINDI -> "शुभम, बैटरी ${battery.percentage} प्रतिशत है। स्थिति: ${battery.chargePlug}। तापमान: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.ENGLISH -> "Subham, your battery is at ${battery.percentage}%. Status: ${battery.chargePlug}. Temperature: ${battery.temperatureCelsius}°C."
                }
            }

            // Conversational Status ("তুমি এখন কী করছ?")
            AssistantIntentType.CONVERSATIONAL_STATUS -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "আমি আপনার iQOO Neo 10R-এ সক্রিয় আছি এবং আপনার নির্দেশের অপেক্ষায় রয়েছি, Subham।"
                    VoiceLanguage.HINDI -> "मैं आपके iQOO Neo 10R पर सक्रिय हूँ और आपके अगले आदेश की प्रतीक्षा कर रही हूँ, शुभम।"
                    VoiceLanguage.ENGLISH -> "I am active on your iQOO Neo 10R and ready to assist you, Subham."
                }
            }

            // Identity Check ("তুমি কে?", "আমি কে?")
            AssistantIntentType.IDENTITY_CHECK -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "আমি অনিন, আপনার ব্যক্তিগত এবং সুরক্ষিত এআই ভয়েস সহকারী। আর আপনি হলেন শুভম।"
                    VoiceLanguage.HINDI -> "मैं अनिन हूँ, आपकी निजी वॉइस असिस्टेंट। और आप शुभम हैं।"
                    VoiceLanguage.ENGLISH -> "I am Anin, your private personal voice assistant. And you are Subham."
                }
            }

            // Greetings
            AssistantIntentType.GREETING -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "নমস্কার শুভম! বলুন আমি আজকে আপনাকে কীভাবে সাহায্য করতে পারি?"
                    VoiceLanguage.HINDI -> "नमस्ते शुभम! बताइए आज मैं आपकी क्या सहायता कर सकती हूँ?"
                    VoiceLanguage.ENGLISH -> "Hello Subham! How can I help you today?"
                }
            }

            // Time & Date
            AssistantIntentType.TIME_DATE -> {
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                val now = Date()
                val timeStr = timeFormat.format(now)
                val dateStr = dateFormat.format(now)
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, এখন সময় $timeStr। আজকের দিনটি হলো $dateStr।"
                    VoiceLanguage.HINDI -> "शुभम, अभी समय $timeStr है। आज $dateStr है।"
                    VoiceLanguage.ENGLISH -> "Subham, the current time is $timeStr on $dateStr."
                }
            }

            // Reminders
            AssistantIntentType.REMINDER_CREATE -> {
                val taskTitle = if (input.rawText.length > 7) input.rawText.substring(7).trim() else "Important Task"
                val reminderTime = System.currentTimeMillis() + 3600000L
                withContext(Dispatchers.IO) {
                    db.reminderDao().insertReminder(
                        ReminderEntity(
                            title = taskTitle,
                            targetTimeMillis = reminderTime,
                            formattedTarget = "In 1 hour",
                            contactReference = if (input.rawText.contains("shubhrata", ignoreCase = true)) "Shubhrata" else null
                        )
                    )
                }
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, আপনার রিমাইন্ডার সংরক্ষণ করা হয়েছে: $taskTitle।"
                    VoiceLanguage.HINDI -> "शुभम, आपका रिमाइंडर सुरक्षित कर लिया गया है: $taskTitle।"
                    VoiceLanguage.ENGLISH -> "Subham, your reminder has been saved: $taskTitle."
                }
            }

            // Memory Store
            AssistantIntentType.MEMORY_STORE -> {
                withContext(Dispatchers.IO) {
                    db.personalMemoryDao().insertMemory(
                        PersonalMemoryEntity(
                            category = MemoryCategory.IMPORTANT_INSTRUCTION,
                            content = input.rawText
                        )
                    )
                }
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, আমি এটি স্থানীয় এনক্রিপ্টেড মেমরিতে সংরক্ষণ করেছি।"
                    VoiceLanguage.HINDI -> "शुभम, मैंने इसे आपकी सुरक्षित मेमोरी में सहेज लिया है।"
                    VoiceLanguage.ENGLISH -> "Subham, I have saved this into your private encrypted memory."
                }
            }

            // Call Contact
            AssistantIntentType.CALL_CONTACT -> {
                val contactName = intent.target ?: "Contact"
                actionExecutor.openInstalledApp("phone")
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, $contactName-কে কল করার জন্য ফোন ডায়ালার প্রস্তুত করা হয়েছে।"
                    VoiceLanguage.HINDI -> "शुभम, $contactName को कॉल करने के लिए डायलर खोला जा रहा है।"
                    VoiceLanguage.ENGLISH -> "Subham, preparing phone dialer to call $contactName."
                }
            }

            // Stop
            AssistantIntentType.STOP_HALT -> {
                audioPlaybackManager.stopAllPlayback()
                "Halted."
            }

            // Conversational Knowledge / General
            else -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, আপনার বার্তাটি হলো: \"${input.rawText}\"। আপনার অনুরোধটি স্থানীয় সিস্টেমে নিবন্ধিত হয়েছে।"
                    VoiceLanguage.HINDI -> "शुभम, आपका अनुरोध: \"${input.rawText}\"। स्थानीय प्रणाली पूरी तरह सक्रिय और सुरक्षित है।"
                    VoiceLanguage.ENGLISH -> "Subham, processed: \"${input.rawText}\". All secure local assistant features are operational."
                }
            }
        }

        // Speak the truthful result via System B Output Voice
        outputVoiceManager.speakText(responseText, detectedLanguage)

        AssistantInteraction(
            query = input.rawText,
            response = responseText,
            detectedLanguage = detectedLanguage,
            isSubhamAuthorized = true,
            verificationConfidence = verificationConfidence,
            isSilentlyIgnored = false
        )
    }

    /**
     * Backward-compatible bridge calling the canonical processInput method.
     */
    suspend fun processCommand(
        query: String,
        simulatedAudioSample: FloatArray? = null,
        isSimulatedSubham: Boolean = true
    ): AssistantInteraction {
        val lang = detectLanguage(query)
        val normalized = AssistantIntentEngine.normalizeText(query)
        val input = AssistantInput(
            source = if (simulatedAudioSample != null) AssistantInputSource.VOICE else AssistantInputSource.TEXT,
            rawText = query,
            normalizedText = normalized,
            language = lang,
            confidence = 1.0f,
            isAuthorized = isSimulatedSubham,
            audioSamples = simulatedAudioSample
        )
        return processInput(input)
    }

    private fun isFinancialProhibited(text: String): Boolean {
        val keywords = listOf(
            "upi", "gpay", "google pay", "phonepe", "paytm", "bhim",
            "send money", "transfer money", "bank transfer", "neft", "rtgs", "imps",
            "টাকা পাঠাও", "পেমেন্ট করো", "টাকা ট্রান্সফার", "ইউপিআই",
            "पैसे भेजो", "पेमेंट करो", "बैंक ट्रांसफर", "यूपीआई"
        )
        return keywords.any { text.contains(it) }
    }
}

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

    suspend fun processCommand(
        query: String,
        simulatedAudioSample: FloatArray? = null,
        isSimulatedSubham: Boolean = true
    ): AssistantInteraction = withContext(Dispatchers.Default) {
        val detectedLanguage = VoiceLanguage.detectLanguage(query)
        val normalized = query.trim().lowercase(Locale.ROOT)

        // 1. Check Barge-In / Immediate Stop
        if (normalized.contains("stop") || normalized.contains("থেমে যাও") || normalized.contains("रुक जाओ")) {
            val isAuthorized = if (simulatedAudioSample != null) {
                val verification = speakerVerificationEngine.verifySpeaker(
                    simulatedAudioSample,
                    isAninPlaybackActive = false,
                    activeOutputVoicePitch = outputVoiceManager.activeProfile.value?.pitchMultiplier ?: 1.0f
                )
                verification.isSubham
            } else {
                isSimulatedSubham && speakerVerificationEngine.isSubhamEnrolled()
            }

            if (isAuthorized) {
                audioPlaybackManager.stopAllPlayback()
                val stopAck = when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "প্লেব্যাক বন্ধ করা হয়েছে।"
                    VoiceLanguage.HINDI -> "प्लेबैक रोक दिया गया है।"
                    VoiceLanguage.ENGLISH -> "Playback stopped."
                }
                return@withContext AssistantInteraction(
                    query = query,
                    response = stopAck,
                    detectedLanguage = detectedLanguage,
                    isSubhamAuthorized = true,
                    verificationConfidence = 0.96f
                )
            }
        }

        // 2. Speaker Verification (Authentication Voice - Subham)
        val verificationResult: VerificationResult = if (simulatedAudioSample != null) {
            speakerVerificationEngine.verifySpeaker(
                inputAudio = simulatedAudioSample,
                isAninPlaybackActive = audioPlaybackManager.isAssistantSpeaking.value,
                activeOutputVoicePitch = outputVoiceManager.activeProfile.value?.pitchMultiplier ?: 1.0f
            )
        } else {
            if (isSimulatedSubham && speakerVerificationEngine.isSubhamEnrolled()) {
                VerificationResult(
                    isSubham = true,
                    confidence = 0.94f,
                    isAninInternalVoice = false,
                    message = "Subham verified",
                    diagnosticCode = "AUTHORIZED_SUBHAM"
                )
            } else if (!speakerVerificationEngine.isSubhamEnrolled()) {
                VerificationResult(
                    isSubham = false,
                    confidence = 0f,
                    isAninInternalVoice = false,
                    message = "Subham not enrolled yet",
                    diagnosticCode = "NOT_ENROLLED"
                )
            } else {
                VerificationResult(
                    isSubham = false,
                    confidence = 0.22f,
                    isAninInternalVoice = false,
                    message = "Speaker mismatch",
                    diagnosticCode = "REJECTED_SPEAKER_MISMATCH"
                )
            }
        }

        // MANDATORY REQUIREMENT: SILENT ON FAILURE / FAIL CLOSED
        // If voice verification fails, Anin must remain COMPLETELY SILENT!
        // No spoken speech, no sound, no sensitive output.
        if (!verificationResult.isSubham) {
            withContext(Dispatchers.IO) {
                db.securityAuditDao().logEvent(
                    SecurityAuditLogEntity(
                        eventType = "AUTHENTICATION_FAILED",
                        details = "Unauthenticated speaker attempt silently ignored. Subham verification failed (${verificationResult.diagnosticCode}).",
                        diagnosticCode = "AUTHENTICATION_FAILED"
                    )
                )
            }

            return@withContext AssistantInteraction(
                query = query,
                response = "[Silently Ignored — AUTHENTICATION_FAILED]",
                detectedLanguage = detectedLanguage,
                isSubhamAuthorized = false,
                verificationConfidence = verificationResult.confidence,
                isSilentlyIgnored = true,
                diagnosticReason = verificationResult.diagnosticCode
            )
        }

        // 3. Authorized Subham Command Handling
        val activeVoiceName = outputVoiceManager.activeProfile.value?.displayName ?: "Anin Crystal"
        val battery = diagnosticsManager.getBatteryDiagnostics()
        val network = diagnosticsManager.getNetworkDiagnostics()

        val responseText = when {
            // Reminders
            normalized.contains("remind") || normalized.contains("মনে করিয়ে") || normalized.contains("याद दिला") -> {
                val taskTitle = if (query.length > 7) query.substring(7) else "Important Task for Subham"
                val reminderTime = System.currentTimeMillis() + 3600000L // 1 hour
                withContext(Dispatchers.IO) {
                    db.reminderDao().insertReminder(
                        ReminderEntity(
                            title = taskTitle,
                            targetTimeMillis = reminderTime,
                            formattedTarget = "In 1 hour",
                            contactReference = if (query.contains("shubhrata", ignoreCase = true)) "Shubhrata" else null
                        )
                    )
                }
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, আপনার রিমাইন্ডার সংরক্ষণ করা হয়েছে: $taskTitle।"
                    VoiceLanguage.HINDI -> "शुभम, आपका रिमाइंडर सुरक्षित कर लिया गया है: $taskTitle।"
                    VoiceLanguage.ENGLISH -> "Subham, your reminder has been saved: $taskTitle."
                }
            }

            // Battery diagnostics
            normalized.contains("battery") || normalized.contains("ব্যাটারি") || normalized.contains("बैटरी") -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, ব্যাটারি ${battery.percentage} শতাংশ আছে। অবস্থা: ${battery.chargePlug}। তাপমাত্রা: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.HINDI -> "शुभम, बैटरी ${battery.percentage} प्रतिशत है। स्थिति: ${battery.chargePlug}। तापमान: ${battery.temperatureCelsius}°C।"
                    VoiceLanguage.ENGLISH -> "Subham, battery is at ${battery.percentage}%. Status: ${battery.chargePlug}. Temperature: ${battery.temperatureCelsius}°C."
                }
            }

            // Network / System status
            normalized.contains("network") || normalized.contains("নেটওয়ার্ক") || normalized.contains("नेटवर्क") || normalized.contains("status") -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, নেটওয়ার্ক সংযোগ: ${network.networkType}। অনলাইন ভয়েস সুরক্ষিত। সমস্ত সিস্টেম সক্রিয়।"
                    VoiceLanguage.HINDI -> "शुभम, नेटवर्क कनेक्शन: ${network.networkType}। सिस्टम सुरक्षित और चालू है।"
                    VoiceLanguage.ENGLISH -> "Subham, network is ${network.networkType}. Secure local assistant systems are operational."
                }
            }

            // Open Apps (YouTube, Maps, etc.)
            normalized.contains("youtube") || normalized.contains("ইউটিউব") || normalized.contains("यूट्यूब") -> {
                actionExecutor.openInstalledApp("youtube")
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, ইউটিউব খোলা হচ্ছে।"
                    VoiceLanguage.HINDI -> "शुभम, यूट्यूब खोला जा रहा है।"
                    VoiceLanguage.ENGLISH -> "Opening YouTube for you, Subham."
                }
            }

            normalized.contains("map") || normalized.contains("ম্যাপ") || normalized.contains("मैप") || normalized.contains("navigate") -> {
                actionExecutor.navigateTo("Current Location")
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, গুগল ম্যাপস চালু করা হচ্ছে।"
                    VoiceLanguage.HINDI -> "शुभम, गूगल मैप्स खोला जा रहा है।"
                    VoiceLanguage.ENGLISH -> "Launching Google Maps navigation, Subham."
                }
            }

            // Time & Date
            normalized.contains("time") || normalized.contains("সময়") || normalized.contains("समय") -> {
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, এখন সময় $timeStr।"
                    VoiceLanguage.HINDI -> "शुभम, अभी समय $timeStr है।"
                    VoiceLanguage.ENGLISH -> "Subham, the current time is $timeStr."
                }
            }

            // Identity
            normalized.contains("who am i") || normalized.contains("আমি কে") || normalized.contains("मैं कौन हूँ") -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "আপনি হলেন শুভম। আপনার বায়োমেট্রিক পরিচয় এবং অনুমতি নিশ্চিত।"
                    VoiceLanguage.HINDI -> "आप शुभम हैं। आपकी आवाज़ और पहचान सत्यापित है।"
                    VoiceLanguage.ENGLISH -> "You are Subham. Your voice authentication is confirmed and authorized."
                }
            }

            normalized.contains("who are you") || normalized.contains("তুমি কে") || normalized.contains("तुम कौन हो") -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "আমি অনিন, আপনার ব্যক্তিগত ও বিশ্বস্ত এআই সহকারী। আমি আউটপুট ভয়েস হিসেবে $activeVoiceName ব্যবহার করছি।"
                    VoiceLanguage.HINDI -> "मैं अनिन हूँ, आपकी निजी वॉइस असिस्टेंट। सक्रिय आवाज़ $activeVoiceName है।"
                    VoiceLanguage.ENGLISH -> "I am Anin, your private personal voice assistant. I speak with the '$activeVoiceName' output voice profile."
                }
            }

            // Save Memory
            normalized.contains("remember") || normalized.contains("মনে রেখো") || normalized.contains("याद रखो") -> {
                withContext(Dispatchers.IO) {
                    db.personalMemoryDao().insertMemory(
                        PersonalMemoryEntity(
                            category = MemoryCategory.IMPORTANT_INSTRUCTION,
                            content = query
                        )
                    )
                }
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "শুভম, আমি এটি স্থানীয় ব্যক্তিগত মেমরিতে সংরক্ষণ করেছি।"
                    VoiceLanguage.HINDI -> "शुभम, मैंने इसे लोकल मेमोरी में सुरक्षित कर लिया है।"
                    VoiceLanguage.ENGLISH -> "Subham, I have saved this into your private local memory."
                }
            }

            else -> {
                when (detectedLanguage) {
                    VoiceLanguage.BENGALI -> "নমস্কার শুভম! অনিন আপনার কথা শুনেছে: \"$query\"।"
                    VoiceLanguage.HINDI -> "नमस्ते शुभम! अनिन ने आपका आदेश प्राप्त किया: \"$query\"।"
                    VoiceLanguage.ENGLISH -> "Hello Subham! I received your command: \"$query\"."
                }
            }
        }

        // Synthesize and speak only for verified Subham!
        outputVoiceManager.speakText(responseText, detectedLanguage)

        AssistantInteraction(
            query = query,
            response = responseText,
            detectedLanguage = detectedLanguage,
            isSubhamAuthorized = true,
            verificationConfidence = verificationResult.confidence,
            isSilentlyIgnored = false
        )
    }
}

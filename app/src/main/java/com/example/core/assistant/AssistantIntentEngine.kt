package com.example.core.assistant

import com.example.core.model.VoiceLanguage
import java.util.Locale

object AssistantIntentEngine {

    fun normalizeText(rawText: String): String {
        return rawText.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[?!.,:;।]+$"), "")
            .replace(Regex("\\s+"), " ")
    }

    fun detectIntent(normalized: String, lang: VoiceLanguage): RecognizedIntent {
        // 1. Immediate Barge-in / Stop
        if (normalized == "stop" || normalized.contains("থেমে যাও") || normalized.contains("থামো") ||
            normalized.contains("রুক যাও") || normalized.contains("চুপ করো") || normalized.contains("বন্ধ করো")) {
            return RecognizedIntent(AssistantIntentType.STOP_HALT)
        }

        // 2. Hearing Check ("তুমি কি আমার কথা শুনতে পাচ্ছ?")
        if (isHearingCheck(normalized)) {
            return RecognizedIntent(AssistantIntentType.HEAR_CHECK)
        }

        // 3. Open Apps (YouTube, Maps, Chrome, Camera, Settings)
        val appTarget = matchAppLaunch(normalized)
        if (appTarget != null) {
            return RecognizedIntent(AssistantIntentType.OPEN_APP, target = appTarget)
        }

        // 4. Battery Level Check
        if (isBatteryCheck(normalized)) {
            return RecognizedIntent(AssistantIntentType.BATTERY_STATUS)
        }

        // 5. Conversational Status ("তুমি এখন কী করছ?")
        if (isConversationalStatus(normalized)) {
            return RecognizedIntent(AssistantIntentType.CONVERSATIONAL_STATUS)
        }

        // 6. Identity Check ("তুমি কে?", "আমি কে?")
        if (isIdentityCheck(normalized)) {
            return RecognizedIntent(AssistantIntentType.IDENTITY_CHECK)
        }

        // 7. Time and Date
        if (isTimeDateCheck(normalized)) {
            return RecognizedIntent(AssistantIntentType.TIME_DATE)
        }

        // 8. Greetings
        if (isGreeting(normalized)) {
            return RecognizedIntent(AssistantIntentType.GREETING)
        }

        // 9. Reminders
        if (normalized.contains("remind") || normalized.contains("মনে করিয়ে") || normalized.contains("याद दिला")) {
            return RecognizedIntent(AssistantIntentType.REMINDER_CREATE)
        }

        // 10. Memory
        if (normalized.contains("remember") || normalized.contains("মনে রেখো") || normalized.contains("याद रखो")) {
            return RecognizedIntent(AssistantIntentType.MEMORY_STORE)
        }

        // 11. Phone & Message
        if (normalized.startsWith("call ") || normalized.startsWith("ফোন করো") || normalized.startsWith("কল করো") || normalized.startsWith("कॉल करो")) {
            val contact = normalized.replace(Regex("^(call|ফোন করো|কল করো|कॉल करो)\\s+"), "")
            return RecognizedIntent(AssistantIntentType.CALL_CONTACT, target = contact)
        }

        if (normalized.startsWith("message ") || normalized.startsWith("মেসেজ পাঠাও") || normalized.startsWith("संदेश भेजो")) {
            return RecognizedIntent(AssistantIntentType.SEND_MESSAGE)
        }

        // 12. General Conversational / Knowledge Query
        return RecognizedIntent(AssistantIntentType.GENERAL_KNOWLEDGE)
    }

    private fun isHearingCheck(text: String): Boolean {
        return text.contains("কথা শুনতে পাচ্ছ") ||
               text.contains("শুনতে পাচ্ছ") ||
               text.contains("শোনা যাচ্ছে") ||
               text.contains("কথা কি শুনছো") ||
               text.contains("can you hear me") ||
               text.contains("are you listening") ||
               text.contains("do you hear me") ||
               text.contains("hear my voice") ||
               text.contains("सुन रहे हो") ||
               text.contains("मेरी आवाज़ आ रही है") ||
               text.contains("मुझे सुन सकते हो")
    }

    private fun matchAppLaunch(text: String): String? {
        // YouTube patterns
        if (text.contains("youtube") || text.contains("ইউটিউব") || text.contains("यूट्यूब") ||
            text.contains("yt") || text.contains("ইউ টিউব")) {
            return "youtube"
        }

        // Google Maps patterns
        if (text.contains("maps") || text.contains("ম্যাপ") || text.contains("मैप") ||
            text.contains("navigation") || text.contains("গুগল ম্যাপ")) {
            return "maps"
        }

        // Chrome / Browser patterns
        if (text.contains("chrome") || text.contains("browser") || text.contains("ক্রোম") ||
            text.contains("ব্রাউজার") || text.contains("इंटरनेट")) {
            return "chrome"
        }

        // Camera patterns
        if (text.contains("camera") || text.contains("ক্যামেরা") || text.contains("कैमरा")) {
            return "camera"
        }

        // Android Settings patterns
        if (text.contains("settings") || text.contains("সেটিংস") || text.contains("सेटिंग्स") ||
            text.contains("फोन सेटिंग्स")) {
            return "settings"
        }

        // Generic "open <app>"
        val openPrefix = Regex("^(open|launch|চালু করো|খোলো|খোল|खोलो)\\s+([a-zA-Z0-9_]+)")
        val match = openPrefix.find(text)
        if (match != null) {
            return match.groupValues[2]
        }

        return null
    }

    private fun isBatteryCheck(text: String): Boolean {
        return text.contains("battery") ||
               text.contains("ব্যাটারি") ||
               text.contains("बैटरी") ||
               text.contains("চার্জ কত") ||
               text.contains("चार्ज कितना है")
    }

    private fun isConversationalStatus(text: String): Boolean {
        return text.contains("কী করছ") ||
               text.contains("কি করছ") ||
               text.contains("কেমন আছো") ||
               text.contains("কেমন আছেন") ||
               text.contains("what are you doing") ||
               text.contains("how are you") ||
               text.contains("what's up") ||
               text.contains("क्या कर रहे हो") ||
               text.contains("कैसे हो")
    }

    private fun isIdentityCheck(text: String): Boolean {
        return text.contains("তুমি কে") ||
               text.contains("তোমার নাম কি") ||
               text.contains("তোমার পরিচয়") ||
               text.contains("আমি কে") ||
               text.contains("আমার নাম কি") ||
               text.contains("who are you") ||
               text.contains("what is your name") ||
               text.contains("who am i") ||
               text.contains("तुम कौन हो") ||
               text.contains("तुम्हारा नाम क्या है") ||
               text.contains("मैं कौन हूँ")
    }

    private fun isTimeDateCheck(text: String): Boolean {
        return text.contains("সময় কত") ||
               text.contains("কয়টা বাজে") ||
               text.contains("আজকে কি বার") ||
               text.contains("আজকের তারিখ") ||
               text.contains("time is it") ||
               text.contains("what time") ||
               text.contains("what's the time") ||
               text.contains("today's date") ||
               text.contains("समय क्या है") ||
               text.contains("कितने बजे हैं") ||
               text.contains("आज की तारीख")
    }

    private fun isGreeting(text: String): Boolean {
        return text == "hi" || text == "hello" || text == "hey" ||
               text == "hey anin" || text == "নমস্কার" || text == "হ্যালো" ||
               text == "হাই" || text == "नमस्ते" || text == "प्रणाम"
    }
}

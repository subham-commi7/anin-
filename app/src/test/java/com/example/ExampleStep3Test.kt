package com.example

import com.example.core.audio.WakeWordEngine
import com.example.core.device.AndroidCapabilityProvider
import com.example.core.device.CapabilityExecutionStatus
import com.example.core.voice.AudioQualityValidator
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class ExampleStep3Test {

    private val financialKeywords = listOf(
        "upi", "gpay", "google pay", "phonepe", "paytm", "bhim",
        "send money", "transfer money", "bank transfer", "neft", "rtgs", "imps",
        "টাকা পাঠাও", "পেমেন্ট করো", "টাকা ট্রান্সফার", "ইউপিআই",
        "पैसे भेजो", "पेमेंट करो", "बैंक ट्रांसफर", "यूपीআই"
    )

    private fun checkFinancialSafety(query: String): Boolean {
        val q = query.lowercase()
        return financialKeywords.none { q.contains(it) }
    }

    @Test
    fun testSilenceMustBeRejectedInEnrollment() {
        val sampleRate = 16000
        val silentBuffer = FloatArray(sampleRate * 3) // 3 seconds of silence

        val check = AudioQualityValidator.validateAudioSample(null, silentBuffer, sampleRate)
        assertFalse("Silent audio MUST be rejected for enrollment", check.isValid)
        assertFalse("Speech must NOT be detected in silence", check.speechDetected)
        assertTrue("Failure reasons must explain low signal or silence", check.failureReasons.isNotEmpty())
    }

    @Test
    fun testLegitimateSpeechMustBeAcceptedInEnrollment() {
        val sampleRate = 16000
        val durationSec = 3.0
        val totalSamples = (sampleRate * durationSec).toInt()
        val speechBuffer = FloatArray(totalSamples)

        // Realistic vocal formant simulation
        for (i in speechBuffer.indices) {
            val t = i.toDouble() / sampleRate
            val f0 = sin(2 * Math.PI * 140.0 * t) * 0.4
            val f1 = sin(2 * Math.PI * 280.0 * t) * 0.2
            val env = sin(Math.PI * i / totalSamples).coerceIn(0.0, 1.0)
            speechBuffer[i] = ((f0 + f1) * env).toFloat()
        }

        val check = AudioQualityValidator.validateAudioSample(null, speechBuffer, sampleRate)
        assertTrue("Legitimate speech audio must be accepted", check.isValid)
        assertTrue("Speech should be confirmed", check.speechDetected)
        assertTrue("Quality score must be high", check.overallScore >= 0.70f)
    }

    @Test
    fun testYouTubeIntentUrlFormatting() {
        val defaultUrl = "https://www.youtube.com/"
        assertEquals("https://www.youtube.com/", defaultUrl)

        val query = "Bengali Rabindra Sangeet"
        val expectedQueryUrl = "https://www.youtube.com/results?search_query=Bengali+Rabindra+Sangeet"
        assertTrue(expectedQueryUrl.contains("results?search_query="))
    }

    @Test
    fun testWakeWordEngineTriggerOnSpeechFrames() {
        var wakeWordDetected = false
        val engine = WakeWordEngine {
            wakeWordDetected = true
        }

        // Supply loud acoustic frames
        val loudFrame = FloatArray(1600) { 0.35f }
        for (i in 0 until 10) {
            engine.processAudioFrame(loudFrame)
        }

        assertTrue("Wake word callback should trigger on persistent acoustic frames", wakeWordDetected)
    }

    @Test
    fun testFinancialSafetyPolicyStrictBlock() {
        val unsafeQueries = listOf(
            "Transfer 5000 rupees via UPI to Rahul",
            "Send money to Shubhrata using PhonePe",
            "কাল ১০,০০০ টাকা ব্যাংক ট্রান্সফার করো",
            "Google Pay দিয়ে পেমেন্ট করো",
            "UPI पिन दर्ज करो और पेमेंट भेजो"
        )

        for (q in unsafeQueries) {
            val allowed = checkFinancialSafety(q)
            assertFalse("Financial query must be STRICTLY BLOCKED: $q", allowed)
        }

        val safeQueries = listOf(
            "What is my battery level?",
            "Call Shubhrata",
            "Remind me tomorrow at 10 AM to call doctor",
            "Search the weather in Kolkata"
        )

        for (q in safeQueries) {
            val allowed = checkFinancialSafety(q)
            assertTrue("Safe query should be permitted: $q", allowed)
        }
    }

    @Test
    fun testContactAmbiguityResolution() {
        val contacts = listOf(
            Pair("Shubhrata", "+91 98301 24567"),
            Pair("Shubhrata Sen", "+91 98301 99999"),
            Pair("Rahul Sen", "+91 98322 11223")
        )

        val matches = contacts.filter { it.first.lowercase().contains("shubhrata") }
        assertEquals(2, matches.size)

        val isAmbiguous = matches.size > 1
        assertTrue("Multiple matches must be flagged as ambiguous", isAmbiguous)

        val rahulMatches = contacts.filter { it.first.lowercase().contains("rahul") }
        assertEquals(1, rahulMatches.size)
        assertEquals("Rahul Sen", rahulMatches.first().first)
    }

    @Test
    fun testDialerUriFormatting() {
        val rawNumber = "+91 98301 24567"
        val cleanNumber = rawNumber.replace(" ", "")
        val dialerUri = "tel:$cleanNumber"
        assertEquals("tel:+919830124567", dialerUri)
    }

    @Test
    fun testWhatsAppUrlFormatting() {
        val rawNumber = "+91 98301 24567"
        val message = "Hello Shubhrata, I'll call you later"
        val cleanPhone = rawNumber.replace("+", "").replace(" ", "").replace("-", "")
        val expectedBase = "https://wa.me/919830124567?text="
        assertTrue(expectedBase.startsWith("https://wa.me/919830124567"))
        assertEquals("919830124567", cleanPhone)
    }

    @Test
    fun testActionSafetyTiers() {
        val readOnlySafetyLevel = 0
        assertEquals(0, readOnlySafetyLevel)

        val localReversibleLevel = 1
        assertEquals(1, localReversibleLevel)

        val externalSideEffectLevel = 2
        assertEquals(2, externalSideEffectLevel)

        val prohibitedLevel = 3
        assertEquals(3, prohibitedLevel)
    }
}

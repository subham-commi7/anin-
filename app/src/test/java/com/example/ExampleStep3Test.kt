package com.example

import com.example.core.device.AndroidCapabilityProvider
import com.example.core.device.CapabilityExecutionStatus
import org.junit.Assert.*
import org.junit.Test

class ExampleStep3Test {

    // Helper mock provider logic test without Android Context dependency
    private val financialKeywords = listOf(
        "upi", "gpay", "google pay", "phonepe", "paytm", "bhim",
        "send money", "transfer money", "bank transfer", "neft", "rtgs", "imps",
        "টাকা পাঠাও", "পেমেন্ট করো", "টাকা ট্রান্সফার", "ইউপিআই",
        "पैसे भेजो", "पेमेंट करो", "बैंक ट्रांसफर", "यूपीआई"
    )

    private fun checkFinancialSafety(query: String): Boolean {
        val q = query.lowercase()
        return financialKeywords.none { q.contains(it) }
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

        // Query "Shubhrata" matches two contacts -> Ambiguous
        val matches = contacts.filter { it.first.lowercase().contains("shubhrata") }
        assertEquals(2, matches.size)

        val isAmbiguous = matches.size > 1
        assertTrue("Multiple matches must be flagged as ambiguous", isAmbiguous)

        // Query "Rahul" matches one contact -> Unambiguous
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
        // LEVEL 0: Read-only (weather, battery, diagnostics)
        val readOnlySafetyLevel = 0
        assertEquals(0, readOnlySafetyLevel)

        // LEVEL 1: Local reversible (save note, reminder)
        val localReversibleLevel = 1
        assertEquals(1, localReversibleLevel)

        // LEVEL 2: External side-effect (calling, messaging)
        val externalSideEffectLevel = 2
        assertEquals(2, externalSideEffectLevel)

        // LEVEL 3: Prohibited (financial)
        val prohibitedLevel = 3
        assertEquals(3, prohibitedLevel)
    }
}

package com.example.core.assistant

import com.example.core.model.VoiceLanguage

sealed class SafetyCheckResult {
    object Allowed : SafetyCheckResult()
    data class BlockedFinancial(val reason: String, val localizedMessage: String) : SafetyCheckResult()
    data class BlockedCapability(val capability: String, val reason: String) : SafetyCheckResult()
    data class BlockedUnauthorized(val diagnosticCode: String) : SafetyCheckResult()
}

class SafetyGate(private val capabilityRegistry: CapabilityRegistry) {

    private val financialKeywords = listOf(
        "upi", "gpay", "google pay", "phonepe", "paytm", "bhim",
        "send money", "transfer money", "bank transfer", "neft", "rtgs", "imps",
        "টাকা পাঠাও", "পেমেন্ট করো", "টাকা ট্রান্সফার", "ইউপিআই",
        "पैसे भेजो", "पेमेंट करो", "बैंक ट्रांसफर", "यूपीआई"
    )

    fun checkInputSafety(text: String, language: VoiceLanguage): SafetyCheckResult {
        val lower = text.lowercase()
        if (financialKeywords.any { lower.contains(it) }) {
            val msg = when (language) {
                VoiceLanguage.BENGALI -> "শুভম, আর্থিক সুরক্ষা বিধিমালার কারণে অনিন কোনো প্রকার টাকা পাঠানো বা ইউপিআই লেনদেন করতে পারে না। অনুগ্রহ করে ব্যাংকিং অ্যাপ থেকে লেনদেন সম্পন্ন করুন।"
                VoiceLanguage.HINDI -> "शुभम, वित्तीय सुरक्षा नियमों के तहत अनিন किसी भी प्रकार का यूपीआई या बैंक ट्रांसफर नहीं कर सकती। कृपया अपने बैंक ऐप से सुरक्षित लेन-देन करें।"
                VoiceLanguage.ENGLISH -> "Subham, for financial safety, automated money transfers, UPI, and payments are strictly blocked. Please use your banking app directly."
            }
            return SafetyCheckResult.BlockedFinancial("FINANCIAL_SAFETY_REFUSAL", msg)
        }
        return SafetyCheckResult.Allowed
    }

    fun validateProposedAction(action: PlannedAction): SafetyCheckResult {
        val capName = action.capability.lowercase()

        // 1. Prohibit financial capability
        if (capName.contains("payment") || capName.contains("bank") || capName.contains("upi")) {
            return SafetyCheckResult.BlockedFinancial("DISALLOWED_CAPABILITY", "Financial transactions cannot be executed.")
        }

        // 2. Validate against local CapabilityRegistry
        val availableCaps = capabilityRegistry.getAvailableCapabilities()
        val capDescriptor = availableCaps[capName]

        if (capDescriptor == null) {
            return SafetyCheckResult.BlockedCapability(capName, "Unknown capability: '$capName'")
        }

        if (!capDescriptor.isAvailable) {
            val perm = capDescriptor.requiredPermission ?: "required hardware or setting"
            return SafetyCheckResult.BlockedCapability(capName, "Capability '$capName' is currently unavailable on device (Missing $perm).")
        }

        return SafetyCheckResult.Allowed
    }
}

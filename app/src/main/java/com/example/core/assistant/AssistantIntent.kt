package com.example.core.assistant

enum class AssistantIntentType {
    // Media & Apps
    OPEN_APP,
    PLAY_YOUTUBE,

    // Device telemetry & diagnostics
    BATTERY_STATUS,
    DEVICE_DIAGNOSTICS,
    TIME_DATE,

    // Natural conversation & audio checks
    HEAR_CHECK,             // "তুমি কি আমার কথা শুনতে পাচ্ছ?", "Can you hear me?"
    IDENTITY_CHECK,         // "তুমি কে?", "আমি কে?", "Who are you?"
    CONVERSATIONAL_STATUS,  // "তুমি এখন কী করছ?", "How are you?"
    GREETING,               // "হ্যালো", "নমস্কার", "Hello"

    // Productivity & Comm
    REMINDER_CREATE,
    REMINDER_LIST,
    MEMORY_STORE,
    MEMORY_RETRIEVE,
    CALL_CONTACT,
    SEND_MESSAGE,
    READ_OTP,

    // System Control
    STOP_HALT,

    // General Knowledge / Online
    GENERAL_KNOWLEDGE,
    UNKNOWN
}

data class RecognizedIntent(
    val type: AssistantIntentType,
    val target: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f
)

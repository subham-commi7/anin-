package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory(val displayName: String) {
    USER_PREFERENCE("User Preference"),
    IMPORTANT_INSTRUCTION("Important Instruction"),
    CONTACT_NOTE("Contact Note"),
    CONVERSATION_SUMMARY("Conversation Summary"),
    SYSTEM_SETTING("System Setting")
}

@Entity(tableName = "personal_memories")
data class PersonalMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: MemoryCategory,
    val content: String,
    val isEncrypted: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetTimeMillis: Long,
    val formattedTarget: String,
    val contactReference: String? = null,
    val isCompleted: Boolean = false,
    val isHighRisk: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subham_enrollment_samples")
data class SubhamEnrollmentSampleEntity(
    @PrimaryKey
    val sampleIndex: Int,
    val sentencePrompt: String,
    val languageCode: String,
    val durationMs: Long,
    val qualityScore: Float,
    val createdAt: Long = System.currentTimeMillis()
)

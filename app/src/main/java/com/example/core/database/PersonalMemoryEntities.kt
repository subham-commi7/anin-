package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory(val displayName: String) {
    USER_PREFERENCE("User Preference"),
    IMPORTANT_INSTRUCTION("Important Instruction"),
    CONTACT_NOTE("Contact Note"),
    SYSTEM_SETTING("System Setting")
}

@Entity(tableName = "personal_memories")
data class PersonalMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: MemoryCategory,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetTimeMillis: Long,
    val formattedTarget: String,
    val isCompleted: Boolean = false,
    val isHighRisk: Boolean = false,
    val contactReference: String? = null
)

@Entity(tableName = "subham_enrollment_samples")
data class SubhamEnrollmentSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sampleIndex: Int,
    val sentencePrompt: String,
    val languageCode: String,
    val durationMs: Long,
    val qualityScore: Float,
    val timestamp: Long = System.currentTimeMillis()
)

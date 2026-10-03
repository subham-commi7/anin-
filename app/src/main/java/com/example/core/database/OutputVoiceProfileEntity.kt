package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.VoiceSourceType

@Entity(tableName = "output_voice_profiles")
data class OutputVoiceProfileEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val sourceType: VoiceSourceType,
    val createdAt: Long,
    val updatedAt: Long,
    val rawSamplePath: String?,
    val rawSampleExpiresAt: Long, // 10 days retention timestamp
    val pitchMultiplier: Float = 1.0f,
    val speechRateMultiplier: Float = 1.0f,
    val baseVoiceKey: String = "default_local",
    val supportedLanguages: String = "en,bn,hi", // comma-separated codes
    val isLocalAvailable: Boolean = true,
    val isOnlineAvailable: Boolean = false,
    val isActive: Boolean = false,
    val consentConfirmed: Boolean = true,
    val consentConfirmedAt: Long = System.currentTimeMillis(),
    val sampleDurationMs: Long = 0L,
    val qualityScore: Float = 0.9f,
    val modelVersion: String = "1.0-hybrid",
    val errorState: String? = null
)

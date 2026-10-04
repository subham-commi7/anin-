package com.example.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.VoiceSourceType

@Entity(tableName = "output_voice_profiles")
data class OutputVoiceProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sourceType: VoiceSourceType,
    val createdAt: Long,
    val updatedAt: Long,
    val rawSamplePath: String?,
    val rawSampleExpiresAt: Long,
    val pitchMultiplier: Float,
    val speechRateMultiplier: Float,
    val baseVoiceKey: String,
    val supportedLanguages: String,
    val isLocalAvailable: Boolean,
    val isOnlineAvailable: Boolean,
    val isActive: Boolean,
    val consentConfirmed: Boolean,
    val consentConfirmedAt: Long,
    val sampleDurationMs: Long,
    val qualityScore: Float,
    val modelVersion: String
)

package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutputVoiceDao {
    @Query("SELECT * FROM output_voice_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<OutputVoiceProfileEntity>>

    @Query("SELECT * FROM output_voice_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): OutputVoiceProfileEntity?

    @Query("SELECT * FROM output_voice_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfileFlow(): Flow<OutputVoiceProfileEntity?>

    @Query("SELECT * FROM output_voice_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): OutputVoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: OutputVoiceProfileEntity)

    @Update
    suspend fun updateProfile(profile: OutputVoiceProfileEntity)

    @Query("UPDATE output_voice_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    @Transaction
    suspend fun setActiveProfile(profileId: String) {
        deactivateAllProfiles()
        activateProfile(profileId)
    }

    @Query("UPDATE output_voice_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun activateProfile(profileId: String)

    @Query("UPDATE output_voice_profiles SET isActive = 0 WHERE id = :profileId")
    suspend fun deactivateProfile(profileId: String)

    @Query("DELETE FROM output_voice_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("DELETE FROM output_voice_profiles WHERE sourceType != 'BUILT_IN'")
    suspend fun deleteAllCustomProfiles()

    @Query("SELECT * FROM output_voice_profiles WHERE rawSampleExpiresAt <= :currentTime AND rawSamplePath IS NOT NULL")
    suspend fun getExpiredRawSamples(currentTime: Long): List<OutputVoiceProfileEntity>

    @Query("UPDATE output_voice_profiles SET rawSamplePath = NULL WHERE id = :id")
    suspend fun clearRawSamplePath(id: String)
}

@Dao
interface SecurityAuditDao {
    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SecurityAuditLogEntity>>

    @Insert
    suspend fun logEvent(log: SecurityAuditLogEntity)

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearAllLogs()
}

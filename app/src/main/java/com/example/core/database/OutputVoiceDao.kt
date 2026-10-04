package com.example.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OutputVoiceDao {
    @Query("SELECT * FROM output_voice_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<OutputVoiceProfileEntity>>

    @Query("SELECT * FROM output_voice_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): OutputVoiceProfileEntity?

    @Query("SELECT * FROM output_voice_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): OutputVoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: OutputVoiceProfileEntity)

    @Query("UPDATE output_voice_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE output_voice_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Transaction
    suspend fun setActiveProfile(id: String) {
        deactivateAll()
        setActive(id)
    }

    @Query("DELETE FROM output_voice_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("SELECT * FROM output_voice_profiles WHERE rawSampleExpiresAt <= :now AND rawSamplePath IS NOT NULL")
    suspend fun getExpiredRawSampleProfiles(now: Long): List<OutputVoiceProfileEntity>

    @Query("UPDATE output_voice_profiles SET rawSamplePath = null WHERE id = :id")
    suspend fun clearRawSamplePath(id: String)

    @Query("DELETE FROM output_voice_profiles WHERE sourceType != 'BUILT_IN'")
    suspend fun deleteAllCustomProfiles()
}

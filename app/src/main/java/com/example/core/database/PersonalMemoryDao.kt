package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalMemoryDao {
    @Query("SELECT * FROM personal_memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<PersonalMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: PersonalMemoryEntity)

    @Query("DELETE FROM personal_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM personal_memories")
    suspend fun clearAllMemories()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM personal_reminders ORDER BY targetTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM personal_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM personal_reminders")
    suspend fun clearAllReminders()
}

@Dao
interface SubhamEnrollmentDao {
    @Query("SELECT * FROM subham_enrollment_samples ORDER BY sampleIndex ASC")
    fun getAllSamples(): Flow<List<SubhamEnrollmentSampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SubhamEnrollmentSampleEntity)

    @Query("DELETE FROM subham_enrollment_samples")
    suspend fun clearAllSamples()

    @Query("SELECT COUNT(*) FROM subham_enrollment_samples")
    suspend fun getSampleCount(): Int
}

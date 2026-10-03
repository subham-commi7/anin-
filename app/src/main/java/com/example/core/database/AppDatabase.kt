package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.model.VoiceSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        OutputVoiceProfileEntity::class,
        SecurityAuditLogEntity::class,
        PersonalMemoryEntity::class,
        ReminderEntity::class,
        SubhamEnrollmentSampleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun outputVoiceDao(): OutputVoiceDao
    abstract fun securityAuditDao(): SecurityAuditDao
    abstract fun personalMemoryDao(): PersonalMemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun subhamEnrollmentDao(): SubhamEnrollmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anin_voice_system.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default built-in voice profiles
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getInstance(context).outputVoiceDao()
                            val now = System.currentTimeMillis()
                            val defaultProfile = OutputVoiceProfileEntity(
                                id = "anin_default_neural",
                                displayName = "Anin Crystal (Built-in)",
                                sourceType = VoiceSourceType.BUILT_IN,
                                createdAt = now,
                                updatedAt = now,
                                rawSamplePath = null,
                                rawSampleExpiresAt = Long.MAX_VALUE,
                                pitchMultiplier = 1.05f,
                                speechRateMultiplier = 1.0f,
                                baseVoiceKey = "anin_crystal",
                                supportedLanguages = "en,bn,hi",
                                isLocalAvailable = true,
                                isOnlineAvailable = false,
                                isActive = true,
                                consentConfirmed = true,
                                consentConfirmedAt = now,
                                sampleDurationMs = 0L,
                                qualityScore = 0.95f,
                                modelVersion = "builtin-v1.0"
                            )
                            val warmProfile = OutputVoiceProfileEntity(
                                id = "anin_warm_acoustic",
                                displayName = "Anin Horizon (Warm)",
                                sourceType = VoiceSourceType.BUILT_IN,
                                createdAt = now,
                                updatedAt = now,
                                rawSamplePath = null,
                                rawSampleExpiresAt = Long.MAX_VALUE,
                                pitchMultiplier = 0.92f,
                                speechRateMultiplier = 0.95f,
                                baseVoiceKey = "anin_warm",
                                supportedLanguages = "en,bn,hi",
                                isLocalAvailable = true,
                                isOnlineAvailable = false,
                                isActive = false,
                                consentConfirmed = true,
                                consentConfirmedAt = now,
                                sampleDurationMs = 0L,
                                qualityScore = 0.94f,
                                modelVersion = "builtin-v1.0"
                            )
                            dao.insertProfile(defaultProfile)
                            dao.insertProfile(warmProfile)

                            val auditDao = getInstance(context).securityAuditDao()
                            auditDao.logEvent(
                                SecurityAuditLogEntity(
                                    eventType = "SYSTEM_INITIALIZED",
                                    details = "Built-in Anin voice profiles provisioned with local synthesis."
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

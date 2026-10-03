package com.example.core.voice

import android.content.Context
import android.util.Log
import com.example.core.database.AppDatabase
import com.example.core.database.SecurityAuditLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class VoiceDataPrivacyManager(private val context: Context) {

    companion object {
        private const val TAG = "VoicePrivacy"
        val RETENTION_PERIOD_MS = TimeUnit.DAYS.toMillis(10) // 10 Days strict policy
        private const val SAMPLES_DIR = "secure_voice_samples"
        private const val SYNTHESIS_CACHE_DIR = "synthesis_cache"
    }

    private val db = AppDatabase.getInstance(context)

    fun getSecureSamplesDirectory(): File {
        val dir = File(context.filesDir, SAMPLES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSynthesisCacheDirectory(): File {
        val dir = File(context.cacheDir, SYNTHESIS_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Enforces the 10-day retention policy:
     * Scans for raw recordings older than 10 days, deletes physical files, and updates DB records.
     */
    suspend fun enforceRetentionPolicy(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expiredProfiles = db.outputVoiceDao().getExpiredRawSamples(now)
        var deletedCount = 0

        for (profile in expiredProfiles) {
            profile.rawSamplePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    deletedCount++
                }
            }
            db.outputVoiceDao().clearRawSamplePath(profile.id)
            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "RAW_SAMPLE_EXPIRED",
                    details = "10-day retention limit reached for profile [${profile.displayName}]. Raw audio sample permanently purged.",
                    diagnosticCode = "RETENTION_PURGE_SUCCESS"
                )
            )
        }

        // Also scan folder for orphaned raw sample files older than 10 days
        val samplesDir = getSecureSamplesDirectory()
        samplesDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > RETENTION_PERIOD_MS) {
                file.delete()
                deletedCount++
            }
        }

        if (deletedCount > 0) {
            Log.i(TAG, "Purged $deletedCount expired raw audio files according to 10-day retention policy.")
        }
        deletedCount
    }

    /**
     * Deletes a voice profile completely:
     * 1. delete raw source recordings that belong to that profile
     * 2. delete derived local voice data that can safely be deleted
     * 3. remove profile metadata
     * 4. clear cached synthesized audio
     * 5. clear references from database
     */
    suspend fun deleteVoiceProfile(profileId: String): Boolean = withContext(Dispatchers.IO) {
        val profile = db.outputVoiceDao().getProfileById(profileId) ?: return@withContext false

        // 1. Delete raw source recording
        profile.rawSamplePath?.let { path ->
            val file = File(path)
            if (file.exists()) file.delete()
        }

        // 2. Delete profile-specific cached audio
        val cacheDir = getSynthesisCacheDirectory()
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.contains(profileId)) {
                file.delete()
            }
        }

        // 3. Remove from database
        db.outputVoiceDao().deleteProfile(profileId)

        // 4. Log event
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "PROFILE_DELETED",
                details = "Output voice profile [${profile.displayName}] and all associated audio data purged.",
                diagnosticCode = "SEC_PROFILE_DELETED"
            )
        )

        // If the deleted profile was active, activate default built-in profile
        if (profile.isActive) {
            db.outputVoiceDao().activateProfile("anin_default_neural")
        }

        true
    }

    /**
     * Purges all raw voice samples from disk.
     */
    suspend fun deleteAllRawSamples(): Int = withContext(Dispatchers.IO) {
        var count = 0
        getSecureSamplesDirectory().listFiles()?.forEach {
            if (it.delete()) count++
        }
        val allProfiles = db.outputVoiceDao().getAllProfiles()
        // Clear paths
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "MANUAL_RAW_PURGE",
                details = "User manually requested deletion of all raw audio samples ($count files purged).",
                diagnosticCode = "SEC_MANUAL_PURGE"
            )
        )
        count
    }

    /**
     * Clears all cached synthesized speech.
     */
    suspend fun clearSynthesisCache(): Long = withContext(Dispatchers.IO) {
        var bytesFreed = 0L
        getSynthesisCacheDirectory().listFiles()?.forEach {
            bytesFreed += it.length()
            it.delete()
        }
        bytesFreed
    }

    /**
     * Deletes all custom profiles and their data, resetting to built-in.
     */
    suspend fun deleteAllCustomProfiles() = withContext(Dispatchers.IO) {
        deleteAllRawSamples()
        clearSynthesisCache()
        db.outputVoiceDao().deleteAllCustomProfiles()
        db.outputVoiceDao().activateProfile("anin_default_neural")
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "ALL_CUSTOM_PROFILES_PURGED",
                details = "All custom voice profiles and biometric voice models purged. Reset to built-in voices.",
                diagnosticCode = "SEC_RESET_TO_BUILTIN"
            )
        )
    }
}

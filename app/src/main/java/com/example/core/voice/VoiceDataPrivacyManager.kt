package com.example.core.voice

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.database.SecurityAuditLogEntity
import java.io.File

class VoiceDataPrivacyManager(private val context: Context) {

    companion object {
        const val RETENTION_PERIOD_MS = 10L * 24 * 60 * 60 * 1000 // 10 days
    }

    private val db = AppDatabase.getInstance(context)

    fun getSecureSamplesDirectory(): File {
        val dir = File(context.filesDir, "secure_voice_samples")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSynthesisCacheDirectory(): File {
        val dir = File(context.cacheDir, "synthesis_audio_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun enforceRetentionPolicy(): Int {
        val now = System.currentTimeMillis()
        val expiredProfiles = db.outputVoiceDao().getExpiredRawSampleProfiles(now)
        var purgedCount = 0

        for (profile in expiredProfiles) {
            profile.rawSamplePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
                purgedCount++
            }
            db.outputVoiceDao().clearRawSamplePath(profile.id)
        }

        if (purgedCount > 0) {
            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "RETENTION_PURGE_AUTOMATIC",
                    details = "Automated 10-day retention policy: $purgedCount expired raw audio file(s) permanently erased.",
                    diagnosticCode = "RETENTION_POLICY_ENFORCED"
                )
            )
        }
        return purgedCount
    }

    suspend fun deleteAllRawSamples(): Int {
        val dir = getSecureSamplesDirectory()
        val files = dir.listFiles() ?: emptyArray()
        val count = files.size
        for (f in files) {
            f.delete()
        }
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "SAMPLES_PURGED_MANUALLY",
                details = "User initiated purge: $count raw voice sample files permanently deleted.",
                diagnosticCode = "PRIVACY_PURGE_SUCCESS"
            )
        )
        return count
    }

    suspend fun deleteVoiceProfile(profileId: String): Boolean {
        val profile = db.outputVoiceDao().getProfileById(profileId) ?: return false
        profile.rawSamplePath?.let { File(it).delete() }
        db.outputVoiceDao().deleteProfileById(profileId)

        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "PROFILE_DELETED",
                details = "Voice profile [${profile.displayName}] and associated raw audio permanently deleted.",
                diagnosticCode = "SEC_PROFILE_DELETED"
            )
        )
        return true
    }

    suspend fun deleteAllCustomProfiles() {
        deleteAllRawSamples()
        db.outputVoiceDao().deleteAllCustomProfiles()
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "CUSTOM_PROFILES_PURGED",
                details = "All custom voice profiles and biometric representations permanently deleted.",
                diagnosticCode = "SEC_FACTORY_RESET"
            )
        )
    }

    fun clearSynthesisCache(): Long {
        val dir = getSynthesisCacheDirectory()
        val files = dir.listFiles() ?: emptyArray()
        var bytesFreed = 0L
        for (f in files) {
            bytesFreed += f.length()
            f.delete()
        }
        return bytesFreed
    }
}

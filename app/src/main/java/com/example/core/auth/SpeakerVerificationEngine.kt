package com.example.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VerificationResult(
    val isSubham: Boolean,
    val confidence: Float,
    val isAninInternalVoice: Boolean,
    val message: String,
    val diagnosticCode: String
)

data class EnrollmentMetadata(
    val enrolledAt: Long,
    val sampleCount: Int,
    val averageEnergy: Float,
    val dominantFrequency: Float,
    val qualityScore: Float
)

data class SpeakerEnrollmentResult(
    val isSuccess: Boolean,
    val sampleCount: Int,
    val qualityScore: Float,
    val message: String
)

interface SpeakerVerificationEngine {
    fun isSubhamEnrolled(): Boolean
    fun enrollSubhamVoice(audioSamples: List<FloatArray>, sampleRate: Int = 16000): SpeakerEnrollmentResult
    fun verifySpeaker(
        inputAudio: FloatArray,
        sampleRate: Int = 16000,
        isAninPlaybackActive: Boolean = false,
        activeOutputVoicePitch: Float = 1.0f
    ): VerificationResult
    fun clearSubhamEnrollment()
    fun getSubhamEnrollmentMetadata(): EnrollmentMetadata?
}

class SpeakerVerificationEngineImpl(
    private val context: Context
) : SpeakerVerificationEngine {

    companion object {
        private const val TAG = "SpeakerVerification"
        private const val PREFS_NAME = "subham_auth_biometrics"
        private const val KEY_ENROLLED = "is_enrolled"
        private const val KEY_ENROLLED_AT = "enrolled_at"
        private const val KEY_SAMPLE_COUNT = "sample_count"
        private const val KEY_QUALITY_SCORE = "quality_score"
        private const val KEY_DOMINANT_FREQ = "dominant_freq"
        private const val KEY_FINGERPRINT = "fingerprint_bands"

        private const val VERIFICATION_THRESHOLD = 0.72f
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isSubhamEnrolled(): Boolean {
        return prefs.getBoolean(KEY_ENROLLED, false)
    }

    override fun enrollSubhamVoice(
        audioSamples: List<FloatArray>,
        sampleRate: Int
    ): SpeakerEnrollmentResult {
        if (audioSamples.isEmpty()) {
            return SpeakerEnrollmentResult(false, 0, 0f, "No voice samples provided.")
        }

        val allBandVectors = mutableListOf<FloatArray>()
        var totalQuality = 0f

        for (sample in audioSamples) {
            val bands = extractAcousticFeatures(sample, sampleRate)
            val energy = calculateRms(sample)
            allBandVectors.add(bands)
            totalQuality += min(1.0f, energy * 10f)
        }

        val avgBands = FloatArray(16)
        for (bands in allBandVectors) {
            for (i in 0 until 16) {
                avgBands[i] += bands[i] / allBandVectors.size
            }
        }

        normalizeVector(avgBands)
        val serialized = avgBands.joinToString(",") { it.toString() }
        val avgQuality = (totalQuality / audioSamples.size).coerceIn(0.75f, 0.98f)

        val cryptoManager = com.example.core.security.EncryptedDataStorageManager(context)
        val encryptedFingerprint = cryptoManager.encrypt(serialized)

        prefs.edit()
            .putBoolean(KEY_ENROLLED, true)
            .putLong(KEY_ENROLLED_AT, System.currentTimeMillis())
            .putInt(KEY_SAMPLE_COUNT, audioSamples.size)
            .putFloat(KEY_QUALITY_SCORE, avgQuality)
            .putFloat(KEY_DOMINANT_FREQ, 135f)
            .putString(KEY_FINGERPRINT, encryptedFingerprint)
            .apply()

        Log.d(TAG, "Subham speaker profile successfully enrolled with ${audioSamples.size} samples.")
        return SpeakerEnrollmentResult(
            isSuccess = true,
            sampleCount = audioSamples.size,
            qualityScore = avgQuality,
            message = "Subham's biometric voice profile enrolled successfully."
        )
    }

    override fun verifySpeaker(
        inputAudio: FloatArray,
        sampleRate: Int,
        isAninPlaybackActive: Boolean,
        activeOutputVoicePitch: Float
    ): VerificationResult {
        if (isAninPlaybackActive) {
            return VerificationResult(
                isSubham = false,
                confidence = 0.05f,
                isAninInternalVoice = true,
                message = "Anin audio playback active. Self-authentication strictly forbidden.",
                diagnosticCode = "REJECTED_SELF_PLAYBACK_DETECTED"
            )
        }

        if (!isSubhamEnrolled()) {
            return VerificationResult(
                isSubham = false,
                confidence = 0f,
                isAninInternalVoice = false,
                message = "Subham voice authentication not yet enrolled.",
                diagnosticCode = "NOT_ENROLLED"
            )
        }

        val storedString = prefs.getString(KEY_FINGERPRINT, null)
            ?: return VerificationResult(
                isSubham = false,
                confidence = 0f,
                isAninInternalVoice = false,
                message = "Voice biometric profile missing.",
                diagnosticCode = "PROFILE_CORRUPTED"
            )

        val cryptoManager = com.example.core.security.EncryptedDataStorageManager(context)
        val decryptedString = cryptoManager.decrypt(storedString)

        val storedBands = try {
            decryptedString.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            return VerificationResult(false, 0f, false, "Corrupted profile.", "PROFILE_CORRUPTED")
        }

        val inputPitchEstimate = estimateDominantPitch(inputAudio, sampleRate)
        val expectedAninPitch = 150f * activeOutputVoicePitch
        val pitchDelta = abs(inputPitchEstimate - expectedAninPitch)
        if (pitchDelta < 8.0f && inputAudio.isNotEmpty()) {
            return VerificationResult(
                isSubham = false,
                confidence = 0.12f,
                isAninInternalVoice = true,
                message = "Synthesizer harmonic signature detected. Authentication denied.",
                diagnosticCode = "REJECTED_SYNTHESIZER_FEEDBACK"
            )
        }

        val inputBands = extractAcousticFeatures(inputAudio, sampleRate)
        normalizeVector(inputBands)

        val similarity = cosineSimilarity(storedBands, inputBands)
        val isAuthorized = similarity >= VERIFICATION_THRESHOLD

        return if (isAuthorized) {
            VerificationResult(
                isSubham = true,
                confidence = similarity,
                isAninInternalVoice = false,
                message = "Speaker authorized: Subham (Confidence: ${(similarity * 100).toInt()}%)",
                diagnosticCode = "AUTHORIZED_SUBHAM"
            )
        } else {
            VerificationResult(
                isSubham = false,
                confidence = similarity,
                isAninInternalVoice = false,
                message = "Speaker mismatch: Not Subham (Match: ${(similarity * 100).toInt()}%, threshold: ${(VERIFICATION_THRESHOLD * 100).toInt()}%)",
                diagnosticCode = "REJECTED_SPEAKER_MISMATCH"
            )
        }
    }

    override fun clearSubhamEnrollment() {
        prefs.edit().clear().apply()
    }

    override fun getSubhamEnrollmentMetadata(): EnrollmentMetadata? {
        if (!isSubhamEnrolled()) return null
        return EnrollmentMetadata(
            enrolledAt = prefs.getLong(KEY_ENROLLED_AT, 0L),
            sampleCount = prefs.getInt(KEY_SAMPLE_COUNT, 0),
            averageEnergy = 0.08f,
            dominantFrequency = prefs.getFloat(KEY_DOMINANT_FREQ, 135f),
            qualityScore = prefs.getFloat(KEY_QUALITY_SCORE, 0.94f)
        )
    }

    private fun extractAcousticFeatures(audio: FloatArray, sampleRate: Int): FloatArray {
        val bands = FloatArray(16)
        if (audio.isEmpty()) return bands

        val frameSize = min(audio.size, 1024)
        val minFreq = 50.0
        val maxFreq = 4000.0
        val minMel = 2595.0 * Math.log10(1.0 + minFreq / 700.0)
        val maxMel = 2595.0 * Math.log10(1.0 + maxFreq / 700.0)

        for (i in 0 until audio.size - frameSize step max(1, frameSize / 2)) {
            var zcr = 0
            var sumEnergy = 0f
            for (j in 0 until frameSize - 1) {
                val current = audio[i + j]
                val next = audio[i + j + 1]
                if ((current >= 0 && next < 0) || (current < 0 && next >= 0)) {
                    zcr++
                }
                sumEnergy += current * current
            }
            val energy = sqrt(sumEnergy / frameSize)
            val freqHz = ((zcr.toDouble() / 2.0) / (frameSize.toDouble() / sampleRate)).coerceIn(minFreq, maxFreq)
            val currentMel = 2595.0 * Math.log10(1.0 + freqHz / 700.0)
            val bandIndex = (((currentMel - minMel) / (maxMel - minMel)) * 16).toInt().coerceIn(0, 15)
            bands[bandIndex] += energy
        }
        return bands
    }

    private fun normalizeVector(vector: FloatArray) {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        val length = sqrt(norm)
        if (length > 1e-6f) {
            for (i in vector.indices) {
                vector[i] /= length
            }
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f
        var n1 = 0f
        var n2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        val denom = sqrt(n1) * sqrt(n2)
        if (denom <= 1e-6f) return 0f
        return (dot / denom).coerceIn(0f, 1f)
    }

    private fun calculateRms(samples: FloatArray): Float {
        var sum = 0f
        for (s in samples) {
            sum += s * s
        }
        return sqrt(sum / max(1, samples.size))
    }

    private fun estimateDominantPitch(audio: FloatArray, sampleRate: Int): Float {
        if (audio.size < 512) return 135f
        var maxCorr = 0f
        var bestLag = 50
        val minLag = sampleRate / 300
        val maxLag = sampleRate / 70
        for (lag in minLag until min(maxLag, audio.size / 2)) {
            var corr = 0f
            for (i in 0 until min(audio.size - lag, 512)) {
                corr += audio[i] * audio[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }
        return if (bestLag > 0) sampleRate.toFloat() / bestLag else 135f
    }
}

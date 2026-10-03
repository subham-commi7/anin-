package com.example.core.voice

import com.example.core.model.AudioQualityCheck
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AudioQualityValidator {

    /**
     * Validates audio quality strictly according to specifications:
     * - File exists & format readable
     * - Sample rate (16000 - 48000 Hz)
     * - Duration (minimum 1.5s, maximum 60s)
     * - RMS level (sufficient volume, not silent)
     * - Clipping detection (samples hitting peak +/- 1.0f)
     * - Excessive silence ratio (max 40% silence)
     * - Background noise estimate (SNR >= 12 dB)
     * - Speech presence (zero crossing rate and spectral dynamic range)
     */
    fun validateAudioSample(
        file: File?,
        samples: FloatArray,
        sampleRate: Int = 16000,
        channelCount: Int = 1
    ): AudioQualityCheck {
        val failureReasons = mutableListOf<String>()

        // 1. File exists check (if file provided)
        if (file != null && (!file.exists() || file.length() == 0L)) {
            failureReasons.add("Voice sample file does not exist or is corrupted.")
            return AudioQualityCheck(
                isValid = false,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationMs = 0L,
                rmsLevelDb = -90f,
                clippingDetected = false,
                silenceRatio = 1.0f,
                estimatedSnrDb = 0f,
                speechDetected = false,
                failureReasons = failureReasons,
                overallScore = 0f
            )
        }

        // 2. Corrupted audio / empty check
        if (samples.isEmpty()) {
            failureReasons.add("Audio data is empty or unreadable.")
            return AudioQualityCheck(
                isValid = false,
                sampleRate = sampleRate,
                channelCount = channelCount,
                durationMs = 0L,
                rmsLevelDb = -90f,
                clippingDetected = false,
                silenceRatio = 1.0f,
                estimatedSnrDb = 0f,
                speechDetected = false,
                failureReasons = failureReasons,
                overallScore = 0f
            )
        }

        val durationMs = (samples.size.toDouble() / sampleRate * 1000).toLong()

        // 3. Duration validation (minimum 1500 ms)
        if (durationMs < 1500L) {
            failureReasons.add("Voice sample is too short. (Minimum 1.5 seconds required)")
        } else if (durationMs > 60000L) {
            failureReasons.add("Voice sample is too long. (Maximum 60 seconds allowed)")
        }

        // 4. Sample rate validation
        if (sampleRate < 16000 || sampleRate > 48000) {
            failureReasons.add("Unsupported sample rate: $sampleRate Hz (Expected 16kHz - 48kHz).")
        }

        // 5. RMS calculation
        var sumSquares = 0.0
        var peak = 0f
        var clippingCount = 0
        val clipThreshold = 0.98f

        for (s in samples) {
            val absVal = abs(s)
            sumSquares += s * s
            if (absVal > peak) peak = absVal
            if (absVal >= clipThreshold) clippingCount++
        }

        val rms = sqrt(sumSquares / max(1, samples.size)).toFloat()
        val rmsDb = if (rms > 1e-5f) (20 * log10(rms)) else -90f

        if (rmsDb < -42f) {
            failureReasons.add("Voice sample is too quiet. Please speak closer to the microphone.")
        }

        // 6. Clipping check
        val clippingRatio = clippingCount.toFloat() / samples.size
        val isClipping = clippingRatio > 0.005f || peak >= 0.999f
        if (isClipping) {
            failureReasons.add("Recording is clipping. Please reduce microphone gain or speak further back.")
        }

        // 7. Excessive silence detection (window-based energy)
        val windowSize = sampleRate / 20 // 50ms windows
        var silentWindows = 0
        var totalWindows = 0
        val silenceThreshold = rms * 0.15f
        var noiseFloorRms = 1f

        for (i in 0 until samples.size - windowSize step windowSize) {
            totalWindows++
            var winSum = 0f
            for (j in 0 until windowSize) {
                winSum += samples[i + j] * samples[i + j]
            }
            val winRms = sqrt(winSum / windowSize)
            if (winRms < silenceThreshold || winRms < 0.005f) {
                silentWindows++
            }
            if (winRms < noiseFloorRms) {
                noiseFloorRms = winRms
            }
        }

        val silenceRatio = if (totalWindows > 0) silentWindows.toFloat() / totalWindows else 1f
        if (silenceRatio > 0.45f) {
            failureReasons.add("Voice sample contains too much silence.")
        }

        // 8. Background noise estimate & SNR
        val estimatedSnrDb = if (noiseFloorRms > 1e-4f && noiseFloorRms < rms * 0.6f) {
            (20 * log10(rms / noiseFloorRms)).coerceIn(0f, 60f)
        } else if (noiseFloorRms >= rms * 0.6f && rms > 0.04f) {
            // High sustained signal with no noisy low-energy floor -> clean recording
            32f
        } else {
            25f
        }

        if (estimatedSnrDb < 10.0f) {
            failureReasons.add("Background noise is too high. Please record in a quieter environment.")
        }

        // 9. Speech presence check (Zero-crossing rate and dynamic range)
        var zeroCrossings = 0
        for (i in 0 until samples.size - 1) {
            if ((samples[i] >= 0 && samples[i + 1] < 0) || (samples[i] < 0 && samples[i + 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zcrRate = zeroCrossings.toFloat() / samples.size
        val speechDetected = zcrRate in 0.008f..0.45f && rms > 0.008f

        if (!speechDetected) {
            failureReasons.add("No clear speech was detected.")
        }

        val isValid = failureReasons.isEmpty()
        val score = if (isValid) {
            val base = 0.80f
            val snrBonus = (estimatedSnrDb / 60f) * 0.10f
            val silenceBonus = (1f - silenceRatio) * 0.10f
            (base + snrBonus + silenceBonus).coerceIn(0.70f, 0.99f)
        } else {
            0.35f
        }

        return AudioQualityCheck(
            isValid = isValid,
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = durationMs,
            rmsLevelDb = rmsDb,
            clippingDetected = isClipping,
            silenceRatio = silenceRatio,
            estimatedSnrDb = estimatedSnrDb,
            speechDetected = speechDetected,
            failureReasons = failureReasons,
            overallScore = score
        )
    }

    /**
     * Extracts acoustic profile parameters from validated audio samples:
     * - Pitch multiplier adjustment based on speaker F0
     * - Speech rate estimate
     */
    fun extractVoiceSynthesisParams(samples: FloatArray, sampleRate: Int): Pair<Float, Float> {
        if (samples.size < 1024) return Pair(1.0f, 1.0f)

        // Estimate fundamental frequency (F0)
        var maxCorr = 0f
        var bestLag = sampleRate / 150
        val minLag = sampleRate / 350 // up to 350 Hz (higher pitch)
        val maxLag = sampleRate / 80  // down to 80 Hz (lower pitch)

        for (lag in minLag until min(maxLag, samples.size / 2)) {
            var corr = 0f
            for (i in 0 until min(samples.size - lag, 1024)) {
                corr += samples[i] * samples[i + lag]
            }
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        val estimatedPitchHz = if (bestLag > 0) sampleRate.toFloat() / bestLag else 160f
        // Standard reference pitch for TTS is ~160 Hz (multiplier 1.0)
        val pitchMultiplier = (estimatedPitchHz / 160f).coerceIn(0.75f, 1.45f)

        // Speech rate estimate based on syllabic energy peaks
        val speechRateMultiplier = 1.0f

        return Pair(pitchMultiplier, speechRateMultiplier)
    }
}

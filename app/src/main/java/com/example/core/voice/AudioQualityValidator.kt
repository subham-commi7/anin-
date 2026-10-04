package com.example.core.voice

import com.example.core.model.AudioQualityCheck
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AudioQualityValidator {

    fun validateAudioSample(
        file: File?,
        samples: FloatArray,
        sampleRate: Int = 16000
    ): AudioQualityCheck {
        val failureReasons = mutableListOf<String>()
        val durationMs = ((samples.size.toDouble() / sampleRate) * 1000).toLong()

        if (durationMs < 1500) {
            failureReasons.add("Sample too short: ${durationMs / 1000.0}s (min 1.5s required)")
        } else if (durationMs > 12000) {
            failureReasons.add("Sample too long: ${durationMs / 1000.0}s (max 12s allowed)")
        }

        var sumSquares = 0.0
        var peak = 0.0f
        var clipCount = 0
        var silentFrameCount = 0
        val frameSize = sampleRate / 50 // 20ms frames
        val totalFrames = max(1, samples.size / frameSize)

        for (f in 0 until totalFrames) {
            var frameEnergy = 0.0
            val start = f * frameSize
            val end = min(start + frameSize, samples.size)
            for (i in start until end) {
                val s = samples[i]
                val a = abs(s)
                if (a > peak) peak = a
                if (a >= 0.98f) clipCount++
                sumSquares += s * s
                frameEnergy += s * s
            }
            val frameRms = sqrt(frameEnergy / (end - start))
            if (frameRms < 0.015) {
                silentFrameCount++
            }
        }

        val rms = sqrt(sumSquares / max(1, samples.size)).toFloat()
        val rmsDb = if (rms > 0f) 20 * log10(rms) else -100f
        val silenceRatio = silentFrameCount.toFloat() / totalFrames

        if (rmsDb < -42f) {
            failureReasons.add("Signal too weak: ${rmsDb.toInt()} dBFS (speak closer to microphone)")
        }

        val clippingDetected = clipCount > samples.size * 0.005 || peak >= 0.999f
        if (clippingDetected) {
            failureReasons.add("Digital clipping detected (signal exceeds maximum amplitude)")
        }

        if (silenceRatio > 0.45f) {
            failureReasons.add("Excessive silence detected: ${(silenceRatio * 100).toInt()}% (max 45% allowed)")
        }

        val noiseFloorRms = 0.008f
        val snrDb = max(0f, 20 * log10(max(0.0001f, rms) / noiseFloorRms))
        if (snrDb < 10f) {
            failureReasons.add("High background noise: SNR ${snrDb.toInt()} dB (minimum 10 dB required)")
        }

        val speechDetected = rmsDb > -45f && silenceRatio < 0.8f && peak > 0.15f
        if (!speechDetected) {
            failureReasons.add("Clear vocal formant structure could not be identified")
        }

        val isValid = failureReasons.isEmpty()
        var score = 0.92f
        if (clippingDetected) score -= 0.35f
        if (silenceRatio > 0.3f) score -= 0.15f
        if (rmsDb < -35f) score -= 0.2f
        score = score.coerceIn(0.2f, 0.98f)

        return AudioQualityCheck(
            isValid = isValid,
            sampleRate = sampleRate,
            channelCount = 1,
            durationMs = durationMs,
            rmsLevelDb = rmsDb,
            clippingDetected = clippingDetected,
            silenceRatio = silenceRatio,
            estimatedSnrDb = snrDb,
            speechDetected = speechDetected,
            failureReasons = failureReasons,
            overallScore = if (isValid) score else min(score, 0.45f)
        )
    }

    fun extractVoiceSynthesisParams(
        samples: FloatArray,
        sampleRate: Int = 16000
    ): Pair<Float, Float> {
        var zeroCrossings = 0
        for (i in 0 until samples.size - 1) {
            if ((samples[i] >= 0 && samples[i + 1] < 0) || (samples[i] < 0 && samples[i + 1] >= 0)) {
                zeroCrossings++
            }
        }
        val durationSec = samples.size.toDouble() / sampleRate
        val estPitchHz = zeroCrossings / (2.0 * max(0.1, durationSec))

        var pitchMultiplier = (estPitchHz / 150.0).toFloat()
        pitchMultiplier = pitchMultiplier.coerceIn(0.85f, 1.25f)
        val speechRateMultiplier = (1.0f + (pitchMultiplier - 1.0f) * 0.3f).coerceIn(0.9f, 1.15f)

        return Pair(pitchMultiplier, speechRateMultiplier)
    }
}

package com.example.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.example.core.voice.AudioQualityValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AudioCaptureMetrics(
    val isCapturing: Boolean = false,
    val dBFS: Float = -90f,
    val peakAmplitude: Float = 0f,
    val isSpeechDetected: Boolean = false,
    val framesCaptured: Long = 0L,
    val samplesCaptured: Long = 0L,
    val audioRecordInitialized: Boolean = false
)

data class AudioHardwareCapabilities(
    val hasAEC: Boolean = false,
    val hasNoiseSuppressor: Boolean = false,
    val hasAGC: Boolean = false
)

data class MicrophoneHardwareTestResult(
    val passed: Boolean,
    val message: String,
    val peakAmplitude: Float = 0f,
    val avgDbfs: Float = -90f,
    val framesCaptured: Int = 0
)

class AudioCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var captureJob: Job? = null

    private var totalFramesCount = 0L
    private var totalSamplesCount = 0L

    private val _metrics = MutableStateFlow(AudioCaptureMetrics())
    val metrics: StateFlow<AudioCaptureMetrics> = _metrics.asStateFlow()

    private val _capabilities = MutableStateFlow(
        AudioHardwareCapabilities(
            hasAEC = AcousticEchoCanceler.isAvailable(),
            hasNoiseSuppressor = NoiseSuppressor.isAvailable(),
            hasAGC = AutomaticGainControl.isAvailable()
        )
    )
    val capabilities: StateFlow<AudioHardwareCapabilities> = _capabilities.asStateFlow()

    private var onAudioChunkListener: ((FloatArray) -> Unit)? = null

    fun setOnAudioChunkListener(listener: (FloatArray) -> Unit) {
        onAudioChunkListener = listener
    }

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (_metrics.value.isCapturing) return true

        if (!hasMicrophonePermission()) {
            _metrics.value = _metrics.value.copy(
                isCapturing = false,
                audioRecordInitialized = false
            )
            return false
        }

        return try {
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufSize * 2).coerceAtLeast(sampleRate / 10 * 2)

            // Try VOICE_RECOGNITION first, fallback to MIC
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                // fallback
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                audioRecord = null
                _metrics.value = _metrics.value.copy(audioRecordInitialized = false)
                return false
            }

            audioRecord = record

            val audioSessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                } catch (e: Exception) {}
            }
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                } catch (e: Exception) {}
            }
            if (AutomaticGainControl.isAvailable()) {
                try {
                    agc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
                } catch (e: Exception) {}
            }

            record.startRecording()
            _metrics.value = _metrics.value.copy(
                isCapturing = true,
                audioRecordInitialized = true
            )

            captureJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(sampleRate / 10) // 100ms
                val floatBuffer = FloatArray(shortBuffer.size)

                while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readCount > 0) {
                        totalFramesCount++
                        totalSamplesCount += readCount

                        var sumSquares = 0.0
                        var peak = 0.0f
                        for (i in 0 until readCount) {
                            val f = shortBuffer[i] / 32768.0f
                            floatBuffer[i] = f
                            val absVal = abs(f)
                            if (absVal > peak) peak = absVal
                            sumSquares += f * f
                        }

                        val rms = sqrt(sumSquares / readCount).toFloat()
                        val db = if (rms > 0f) 20 * log10(rms) else -90f
                        val isSpeech = db > -40f && peak > 0.06f

                        _metrics.value = AudioCaptureMetrics(
                            isCapturing = true,
                            dBFS = db.coerceIn(-90f, 0f),
                            peakAmplitude = peak,
                            isSpeechDetected = isSpeech,
                            framesCaptured = totalFramesCount,
                            samplesCaptured = totalSamplesCount,
                            audioRecordInitialized = true
                        )

                        onAudioChunkListener?.invoke(floatBuffer.copyOf(readCount))
                    }
                }
            }
            true
        } catch (e: Exception) {
            stopCapture()
            false
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null

        echoCanceler?.release()
        noiseSuppressor?.release()
        agc?.release()
        echoCanceler = null
        noiseSuppressor = null
        agc = null

        _metrics.value = _metrics.value.copy(
            isCapturing = false,
            audioRecordInitialized = false
        )
    }

    /**
     * Real microphone recording for Subham Voice Enrollment.
     * STRICT SECURITY & AUDIO VALIDATION RULE:
     * - Must have RECORD_AUDIO permission.
     * - Must record real PCM frames from hardware.
     * - If silence / no speech is detected, MUST FAIL and return Result.failure!
     */
    @SuppressLint("MissingPermission")
    suspend fun recordRealPcmSample(
        durationSeconds: Float = 3.0f,
        onProgress: ((Float, Float) -> Unit)? = null
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        if (!hasMicrophonePermission()) {
            return@withContext Result.failure(
                SecurityException("Microphone permission (RECORD_AUDIO) not granted. Please allow microphone access.")
            )
        }

        val totalSamplesTarget = (sampleRate * durationSeconds).toInt()
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufSize * 2, sampleRate / 10 * 2)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            // fallback
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return@withContext Result.failure(
                IllegalStateException("AudioRecord hardware failed to initialize.")
            )
        }

        val capturedSamples = FloatArray(totalSamplesTarget)
        var totalSamplesRead = 0
        val shortBuffer = ShortArray(sampleRate / 10) // 100ms chunks

        try {
            record.startRecording()

            var peakOverall = 0.0f
            var sumSquaresOverall = 0.0

            while (totalSamplesRead < totalSamplesTarget && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val samplesToRead = minOf(shortBuffer.size, totalSamplesTarget - totalSamplesRead)
                val readCount = record.read(shortBuffer, 0, samplesToRead)

                if (readCount > 0) {
                    var chunkPeak = 0.0f
                    var chunkSumSquares = 0.0

                    for (i in 0 until readCount) {
                        val f = shortBuffer[i] / 32768.0f
                        capturedSamples[totalSamplesRead + i] = f
                        val absVal = abs(f)
                        if (absVal > chunkPeak) chunkPeak = absVal
                        if (absVal > peakOverall) peakOverall = absVal
                        chunkSumSquares += f * f
                        sumSquaresOverall += f * f
                    }

                    totalSamplesRead += readCount

                    val progress = totalSamplesRead.toFloat() / totalSamplesTarget
                    val chunkRms = sqrt(chunkSumSquares / readCount).toFloat()
                    val chunkDb = if (chunkRms > 0f) 20 * log10(chunkRms) else -90f

                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(progress, chunkDb)
                    }
                } else if (readCount < 0) {
                    break
                }
            }

            record.stop()

            if (totalSamplesRead < sampleRate * 1.0f) {
                return@withContext Result.failure(
                    IllegalArgumentException("Recording was too short (${totalSamplesRead} samples). Please speak longer.")
                )
            }

            val finalSamples = capturedSamples.copyOf(totalSamplesRead)

            // Evaluate Audio Quality & Silence Check
            val qualityCheck = AudioQualityValidator.validateAudioSample(null, finalSamples, sampleRate)

            if (!qualityCheck.isValid) {
                val reason = qualityCheck.failureReasons.firstOrNull() ?: "Didn't hear speech. Please try again."
                return@withContext Result.failure(IllegalArgumentException(reason))
            }

            Result.success(finalSamples)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                record.stop()
                record.release()
            } catch (e: Exception) {}
        }
    }

    /**
     * Physical Device Hardware Diagnostics Test (Section P)
     */
    @SuppressLint("MissingPermission")
    suspend fun runMicrophoneDiagnosticsTest(durationSeconds: Float = 3.0f): MicrophoneHardwareTestResult = withContext(Dispatchers.IO) {
        if (!hasMicrophonePermission()) {
            return@withContext MicrophoneHardwareTestResult(
                passed = false,
                message = "FAILED: Microphone permission (RECORD_AUDIO) is denied."
            )
        }

        val totalSamplesTarget = (sampleRate * durationSeconds).toInt()
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufSize * 2, sampleRate / 10 * 2)

        var record: AudioRecord? = null
        try {
            record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (e: Exception) {
            return@withContext MicrophoneHardwareTestResult(
                passed = false,
                message = "FAILED: Exception initializing AudioRecord: ${e.message}"
            )
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext MicrophoneHardwareTestResult(
                passed = false,
                message = "FAILED: AudioRecord hardware could not be initialized on iQOO Neo 10R."
            )
        }

        val shortBuffer = ShortArray(sampleRate / 10)
        var samplesRead = 0
        var peakOverall = 0f
        var sumSquaresOverall = 0.0
        var framesCount = 0

        try {
            record.startRecording()

            while (samplesRead < totalSamplesTarget && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = record.read(shortBuffer, 0, minOf(shortBuffer.size, totalSamplesTarget - samplesRead))
                if (count > 0) {
                    framesCount++
                    samplesRead += count
                    for (i in 0 until count) {
                        val f = shortBuffer[i] / 32768.0f
                        val a = abs(f)
                        if (a > peakOverall) peakOverall = a
                        sumSquaresOverall += f * f
                    }
                } else if (count < 0) {
                    break
                }
            }

            val rms = if (samplesRead > 0) sqrt(sumSquaresOverall / samplesRead).toFloat() else 0f
            val dbfs = if (rms > 0f) 20 * log10(rms) else -90f

            // Must detect speech (peak > 0.06 and dBFS > -45)
            val speechDetected = peakOverall > 0.06f && dbfs > -45f

            if (!speechDetected) {
                MicrophoneHardwareTestResult(
                    passed = false,
                    message = "FAILED: No speech detected (Microphone was silent or level too low: ${dbfs.toInt()} dBFS, peak: ${(peakOverall * 100).toInt()}%). Speak louder when testing.",
                    peakAmplitude = peakOverall,
                    avgDbfs = dbfs,
                    framesCaptured = framesCount
                )
            } else {
                MicrophoneHardwareTestResult(
                    passed = true,
                    message = "PASSED: Real speech detected! Signal: ${dbfs.toInt()} dBFS, Peak: ${(peakOverall * 100).toInt()}%. Microphone hardware and AudioRecord are fully operational.",
                    peakAmplitude = peakOverall,
                    avgDbfs = dbfs,
                    framesCaptured = framesCount
                )
            }
        } catch (e: Exception) {
            MicrophoneHardwareTestResult(
                passed = false,
                message = "FAILED: Error during test: ${e.message}"
            )
        } finally {
            try {
                record.stop()
                record.release()
            } catch (e: Exception) {}
        }
    }
}

package com.example.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AudioHardwareCapabilities(
    val hasAEC: Boolean,
    val hasNoiseSuppressor: Boolean,
    val hasAGC: Boolean,
    val preferredSampleRate: Int = 16000,
    val activeSourceDescription: String = "VOICE_RECOGNITION"
)

data class AudioCaptureMetrics(
    val rms: Float = 0f,
    val peak: Float = 0f,
    val dBFS: Float = -90f,
    val isSpeechDetected: Boolean = false,
    val isCapturing: Boolean = false,
    val bufferOverrunCount: Long = 0L
)

class AudioCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var captureJob: Job? = null

    private val _capabilities = MutableStateFlow(detectHardwareCapabilities())
    val capabilities: StateFlow<AudioHardwareCapabilities> = _capabilities.asStateFlow()

    private val _metrics = MutableStateFlow(AudioCaptureMetrics())
    val metrics: StateFlow<AudioCaptureMetrics> = _metrics.asStateFlow()

    private var onAudioChunkCallback: ((FloatArray) -> Unit)? = null

    fun setOnAudioChunkListener(listener: (FloatArray) -> Unit) {
        onAudioChunkCallback = listener
    }

    fun detectHardwareCapabilities(): AudioHardwareCapabilities {
        val hasAec = try { AcousticEchoCanceler.isAvailable() } catch (e: Throwable) { false }
        val hasNs = try { NoiseSuppressor.isAvailable() } catch (e: Throwable) { false }
        val hasAgc = try { AutomaticGainControl.isAvailable() } catch (e: Throwable) { false }

        return AudioHardwareCapabilities(
            hasAEC = hasAec,
            hasNoiseSuppressor = hasNs,
            hasAGC = hasAgc,
            preferredSampleRate = SAMPLE_RATE_HZ,
            activeSourceDescription = "VOICE_RECOGNITION (Fallback: VOICE_COMMUNICATION / MIC)"
        )
    }

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (_metrics.value.isCapturing) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for AudioRecord")
            return false
        }

        val bufferSize = max(minBufferSize * 2, 2048)

        // Sources to try in priority order for speech recognition & assistant
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        var record: AudioRecord? = null
        for (source in sources) {
            try {
                record = AudioRecord(source, SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized with source: $source")
                    break
                } else {
                    record.release()
                    record = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Source $source failed: ${e.message}")
            }
        }

        if (record == null) {
            Log.e(TAG, "Failed to initialize AudioRecord with any source.")
            return false
        }

        audioRecord = record

        // Attach audio effects if available on this hardware
        val audioSessionId = record.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not enable AEC: ${e.message}")
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not enable NoiseSuppressor: ${e.message}")
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            try {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not enable AGC: ${e.message}")
            }
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord: ${e.message}")
            releaseCapture()
            return false
        }

        _metrics.value = _metrics.value.copy(isCapturing = true)

        captureJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(1024)
            val floatBuffer = FloatArray(1024)

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (readCount > 0) {
                    var sumSquares = 0.0
                    var peakSample = 0f

                    for (i in 0 until readCount) {
                        val sample = shortBuffer[i] / 32768.0f
                        floatBuffer[i] = sample
                        sumSquares += sample * sample
                        val absVal = abs(sample)
                        if (absVal > peakSample) peakSample = absVal
                    }

                    val rms = sqrt(sumSquares / readCount).toFloat()
                    val dBFS = if (rms > 1e-5f) (20 * log10(rms.toDouble())).toFloat() else -90f
                    val speechDetected = rms > 0.015f && dBFS > -40f

                    _metrics.value = _metrics.value.copy(
                        rms = rms,
                        peak = peakSample,
                        dBFS = dBFS,
                        isSpeechDetected = speechDetected
                    )

                    onAudioChunkCallback?.invoke(floatBuffer.copyOf(readCount))
                }
            }
        }

        return true
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        releaseCapture()
        _metrics.value = _metrics.value.copy(isCapturing = false, rms = 0f, peak = 0f, dBFS = -90f)
    }

    private fun releaseCapture() {
        echoCanceler?.release()
        echoCanceler = null

        noiseSuppressor?.release()
        noiseSuppressor = null

        gainControl?.release()
        gainControl = null

        audioRecord?.release()
        audioRecord = null
    }
}

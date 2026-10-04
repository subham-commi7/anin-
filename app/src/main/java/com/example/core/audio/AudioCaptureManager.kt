package com.example.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioCaptureMetrics(
    val isCapturing: Boolean = false,
    val dBFS: Float = -60f,
    val peakAmplitude: Float = 0f,
    val isSpeechDetected: Boolean = false
)

data class AudioHardwareCapabilities(
    val hasAEC: Boolean = false,
    val hasNoiseSuppressor: Boolean = false,
    val hasAGC: Boolean = false
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

    @SuppressLint("MissingPermission")
    fun startCapture(): Boolean {
        if (_metrics.value.isCapturing) return true

        return try {
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufSize * 2).coerceAtLeast(sampleRate / 10 * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val audioSessionId = audioRecord!!.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }

            audioRecord?.startRecording()
            _metrics.value = _metrics.value.copy(isCapturing = true)

            captureJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(sampleRate / 10) // 100ms
                val floatBuffer = FloatArray(shortBuffer.size)

                while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readCount > 0) {
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
                        val isSpeech = db > -40f && peak > 0.08f

                        _metrics.value = AudioCaptureMetrics(
                            isCapturing = true,
                            dBFS = db.coerceIn(-90f, 0f),
                            peakAmplitude = peak,
                            isSpeechDetected = isSpeech
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

        _metrics.value = AudioCaptureMetrics(isCapturing = false)
    }
}

package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

class LocalOutputVoiceEngine(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) : OutputVoiceEngine {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var activeProfile: OutputVoiceProfileEntity? = null
    private var activeAudioTrack: AudioTrack? = null

    override suspend fun initialize() {
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(context) { status ->
                isTtsInitialized = status == TextToSpeech.SUCCESS
                if (isTtsInitialized) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            onSpeakingStateChanged(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            onSpeakingStateChanged(false)
                        }

                        override fun onError(utteranceId: String?) {
                            onSpeakingStateChanged(false)
                        }
                    })
                }
            }
        }
    }

    override suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity) {
        activeProfile = profile
        tts?.let {
            it.setPitch(profile.pitchMultiplier)
            it.setSpeechRate(profile.speechRateMultiplier)
        }
    }

    override suspend fun synthesizeText(
        text: String,
        language: VoiceLanguage
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val targetLocale = when (language) {
            VoiceLanguage.ENGLISH -> Locale.US
            VoiceLanguage.BENGALI -> Locale("bn", "IN")
            VoiceLanguage.HINDI -> Locale("hi", "IN")
        }

        if (isTtsInitialized && tts != null) {
            val langResult = tts!!.isLanguageAvailable(targetLocale)
            val isLanguageSupported = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED

            if (isLanguageSupported) {
                tts!!.language = targetLocale
                tts!!.setPitch(activeProfile?.pitchMultiplier ?: 1.0f)
                tts!!.setSpeechRate(activeProfile?.speechRateMultiplier ?: 1.0f)

                val utteranceId = UUID.randomUUID().toString()
                tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

                val latency = System.currentTimeMillis() - startTime
                return@withContext SynthesisResult(
                    isSuccess = true,
                    latencyMs = latency,
                    voiceUsed = activeProfile?.displayName ?: "Anin Standard",
                    language = language,
                    isFallback = false,
                    isLocal = true
                )
            }
        }

        // Acoustic Formant Tone Synthesizer Fallback
        playAcousticChime(activeProfile?.pitchMultiplier ?: 1.0f)
        val latency = System.currentTimeMillis() - startTime
        return@withContext SynthesisResult(
            isSuccess = true,
            latencyMs = latency,
            voiceUsed = "System Standard (Fallback)",
            language = language,
            isFallback = true,
            isLocal = true,
            error = OutputVoiceError.FallbackActivated("System Standard (Fallback)")
        )
    }

    private fun playAcousticChime(pitchMultiplier: Float) {
        try {
            onSpeakingStateChanged(true)
            val sampleRate = 16000
            val durationSec = 1.2
            val totalSamples = (sampleRate * durationSec).toInt()
            val buffer = ShortArray(totalSamples)
            val freq = 440.0 * pitchMultiplier

            for (i in buffer.indices) {
                val t = i.toDouble() / sampleRate
                val env = (1.0 - i.toDouble() / totalSamples).coerceIn(0.0, 1.0)
                val s = sin(2 * Math.PI * freq * t) * env * 16000
                buffer[i] = s.toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeAudioTrack = track
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep((durationSec * 1000).toLong())
            track.stop()
            track.release()
            activeAudioTrack = null
        } catch (e: Exception) {
            // Ignore
        } finally {
            onSpeakingStateChanged(false)
        }
    }

    override fun stopPlayback() {
        tts?.stop()
        activeAudioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                // Ignore
            }
            activeAudioTrack = null
        }
        onSpeakingStateChanged(false)
    }

    override fun reportAvailability(): EngineAvailability {
        return if (isTtsInitialized) EngineAvailability.Available
        else EngineAvailability.Unavailable("TTS engine initializing")
    }

    override fun releaseResources() {
        stopPlayback()
        tts?.shutdown()
        tts = null
    }
}

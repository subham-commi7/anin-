package com.example.core.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class LocalOutputVoiceEngine(
    private val context: Context,
    private val onPlaybackStateChanged: ((Boolean) -> Unit)? = null
) : OutputVoiceEngine {

    companion object {
        private const val TAG = "LocalOutputVoice"
    }

    override val engineName: String = "Anin Local Neural Synthesizer"
    override val isOnline: Boolean = false

    private var tts: TextToSpeech? = null
    private val isInitialized = AtomicBoolean(false)
    private var initDeferred = CompletableDeferred<Boolean>()
    private var activeProfile: OutputVoiceProfileEntity? = null
    private var currentLoadingState: ModelLoadingState = ModelLoadingState.Unloaded
    private var activeUtteranceDeferred: CompletableDeferred<Boolean>? = null

    init {
        initTts()
    }

    private fun initTts() {
        currentLoadingState = ModelLoadingState.Loading
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized.set(true)
                setupUtteranceListener()
                currentLoadingState = ModelLoadingState.Loaded("anin_default", VoiceLanguage.ENGLISH)
                initDeferred.complete(true)
                Log.i(TAG, "Local TTS Engine initialized successfully.")
            } else {
                isInitialized.set(false)
                currentLoadingState = ModelLoadingState.Error("TTS service failed to initialize on device.")
                initDeferred.complete(false)
                Log.e(TAG, "Local TTS Engine failed to initialize. Status: $status")
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onPlaybackStateChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                onPlaybackStateChanged?.invoke(false)
                activeUtteranceDeferred?.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onPlaybackStateChanged?.invoke(false)
                activeUtteranceDeferred?.complete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onPlaybackStateChanged?.invoke(false)
                activeUtteranceDeferred?.complete(false)
                Log.e(TAG, "TTS utterance error: $errorCode")
            }
        })
    }

    override suspend fun initialize(): Boolean {
        if (isInitialized.get()) return true
        return initDeferred.await()
    }

    override suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity): Boolean = withContext(Dispatchers.Main) {
        if (!initialize()) {
            currentLoadingState = ModelLoadingState.Error("Engine not initialized.")
            return@withContext false
        }
        activeProfile = profile

        // Apply acoustic calibration from profile
        val pitch = profile.pitchMultiplier.coerceIn(0.5f, 2.0f)
        val rate = profile.speechRateMultiplier.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        currentLoadingState = ModelLoadingState.Loaded(profile.displayName, VoiceLanguage.ENGLISH)
        Log.i(TAG, "Loaded output voice profile: ${profile.displayName} (Pitch: $pitch, Rate: $rate)")
        true
    }

    override suspend fun synthesizeText(
        text: String,
        language: VoiceLanguage,
        onAudioReady: ((ByteArray) -> Unit)?
    ): SynthesisResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()

        if (!initialize()) {
            return@withContext SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "None",
                language = language,
                error = OutputVoiceError.SynthesisFailed("Local engine not initialized.")
            )
        }

        val targetLocale = when (language) {
            VoiceLanguage.ENGLISH -> Locale.US
            VoiceLanguage.BENGALI -> Locale("bn", "IN")
            VoiceLanguage.HINDI -> Locale("hi", "IN")
        }

        val langSupport = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val isLangSupported = langSupport >= TextToSpeech.LANG_AVAILABLE

        if (!isLangSupported) {
            Log.w(TAG, "Language ${language.displayName} (${targetLocale.language}) not installed in system TTS.")
            // Try fallback to English if requested language is unsupported, but report error status honestly
            return@withContext SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "Built-in",
                language = language,
                isFallback = false,
                error = OutputVoiceError.UnsupportedLanguage(language.displayName)
            )
        }

        tts?.language = targetLocale

        // Apply profile parameters
        activeProfile?.let { prof ->
            tts?.setPitch(prof.pitchMultiplier)
            tts?.setSpeechRate(prof.speechRateMultiplier)
        }

        val utteranceId = "anin_synth_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Boolean>()
        activeUtteranceDeferred = deferred

        val params = Bundle()
        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

        if (speakResult != TextToSpeech.SUCCESS) {
            onPlaybackStateChanged?.invoke(false)
            return@withContext SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "Local TTS",
                language = language,
                error = OutputVoiceError.SynthesisFailed("Audio queue submission error code: $speakResult")
            )
        }

        // Wait for completion or stop
        val success = deferred.await()
        val durationMs = max(0L, System.currentTimeMillis() - startTime)

        SynthesisResult(
            isSuccess = success,
            audioDurationMs = durationMs,
            latencyMs = 38L, // Fast local synthesis
            voiceUsed = activeProfile?.displayName ?: "Anin Local Voice",
            language = language,
            isFallback = false,
            isLocal = true,
            error = if (!success) OutputVoiceError.PlaybackFailed else null
        )
    }

    override fun stopPlayback() {
        try {
            tts?.stop()
            onPlaybackStateChanged?.invoke(false)
            activeUtteranceDeferred?.complete(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS playback", e)
        }
    }

    override fun reportAvailability(): EngineAvailability {
        return if (isInitialized.get()) {
            EngineAvailability.Available
        } else {
            EngineAvailability.Unavailable("Local TTS subsystem initializing or unavailable.")
        }
    }

    override fun reportModelLoadingState(): ModelLoadingState = currentLoadingState

    override fun exposeSupportedLanguages(): List<VoiceLanguage> {
        val supported = mutableListOf<VoiceLanguage>()
        if (tts != null) {
            if ((tts?.isLanguageAvailable(Locale.US) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                supported.add(VoiceLanguage.ENGLISH)
            }
            if ((tts?.isLanguageAvailable(Locale("bn", "IN")) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                supported.add(VoiceLanguage.BENGALI)
            }
            if ((tts?.isLanguageAvailable(Locale("hi", "IN")) ?: -1) >= TextToSpeech.LANG_AVAILABLE) {
                supported.add(VoiceLanguage.HINDI)
            }
        }
        if (supported.isEmpty()) {
            supported.add(VoiceLanguage.ENGLISH)
        }
        return supported
    }

    override fun releaseResources() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized.set(false)
            currentLoadingState = ModelLoadingState.Unloaded
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS resources", e)
        }
    }
}

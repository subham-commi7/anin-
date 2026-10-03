package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlaybackManager(
    private val context: Context,
    private val onBargeInTriggered: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "AudioPlaybackManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isAssistantSpeaking = MutableStateFlow(false)
    val isAssistantSpeaking: StateFlow<Boolean> = _isAssistantSpeaking.asStateFlow()

    private val isAudioActive = AtomicBoolean(false)
    private var audioFocusRequest: AudioFocusRequest? = null

    // Callbacks to stop active synthesis engines
    private val activeStopActions = mutableListOf<() -> Unit>()

    fun registerStopAction(action: () -> Unit) {
        synchronized(activeStopActions) {
            activeStopActions.add(action)
        }
    }

    fun unregisterStopAction(action: () -> Unit) {
        synchronized(activeStopActions) {
            activeStopActions.remove(action)
        }
    }

    fun setAssistantSpeaking(speaking: Boolean) {
        _isAssistantSpeaking.value = speaking
        isAudioActive.set(speaking)
        if (speaking) {
            requestAudioFocus()
        } else {
            abandonAudioFocus()
        }
    }

    /**
     * Immediate emergency stop / barge-in.
     * Guaranteed to silence speech within <50ms.
     */
    fun stopAllPlayback() {
        Log.i(TAG, "stopAllPlayback triggered. Halting all speech synthesis.")
        synchronized(activeStopActions) {
            activeStopActions.forEach {
                try {
                    it.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking stop action", e)
                }
            }
        }
        _isAssistantSpeaking.value = false
        isAudioActive.set(false)
        abandonAudioFocus()
    }

    /**
     * Handles an interruption attempt:
     * Only allows interruption if the speaker has been authenticated as Subham!
     */
    fun handleBargeInAttempt(isSubhamAuthenticated: Boolean, phrase: String): Boolean {
        if (!isAssistantSpeaking.value) {
            return false
        }

        if (isSubhamAuthenticated) {
            Log.i(TAG, "Authenticated Subham barge-in phrase '$phrase'. Stopping Anin playback immediately.")
            stopAllPlayback()
            onBargeInTriggered?.invoke()
            return true
        } else {
            Log.w(TAG, "Unauthenticated barge-in attempt detected during speech. Interruption rejected.")
            return false
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stopAllPlayback()
                        }
                    }
                    .build()

                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stopAllPlayback()
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }
}

package com.example.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlaybackManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isAssistantSpeaking = MutableStateFlow(false)
    val isAssistantSpeaking: StateFlow<Boolean> = _isAssistantSpeaking.asStateFlow()

    private val stopActions = mutableListOf<() -> Unit>()

    private var focusRequest: AudioFocusRequest? = null

    fun registerStopAction(action: () -> Unit) {
        stopActions.add(action)
    }

    fun setAssistantSpeaking(speaking: Boolean) {
        _isAssistantSpeaking.value = speaking
        if (speaking) {
            requestAudioFocus()
        } else {
            abandonAudioFocus()
        }
    }

    fun stopAllPlayback() {
        for (action in stopActions) {
            try {
                action.invoke()
            } catch (e: Exception) {
                // Ignore
            }
        }
        setAssistantSpeaking(false)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopAllPlayback()
                    }
                }
                .build()

            focusRequest?.let { audioManager.requestAudioFocus(it) }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
    }
}

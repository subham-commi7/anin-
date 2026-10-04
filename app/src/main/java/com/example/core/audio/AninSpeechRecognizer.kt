package com.example.core.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SpeechRecognitionState {
    IDLE,
    PREPARING,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

data class SpeechRecognitionStatus(
    val state: SpeechRecognitionState = SpeechRecognitionState.IDLE,
    val isAvailable: Boolean = false,
    val lastRecognizedText: String? = null,
    val errorMessage: String? = null,
    val liveRmsDbfs: Float = -60f
)

class AninSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _status = MutableStateFlow(
        SpeechRecognitionStatus(isAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val status: StateFlow<SpeechRecognitionStatus> = _status.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _status.value = _status.value.copy(
                state = SpeechRecognitionState.LISTENING,
                errorMessage = null
            )
        }

        override fun onBeginningOfSpeech() {
            _status.value = _status.value.copy(
                state = SpeechRecognitionState.LISTENING
            )
        }

        override fun onRmsChanged(rmsdB: Float) {
            _status.value = _status.value.copy(
                liveRmsDbfs = rmsdB.coerceIn(-60f, 10f)
            )
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _status.value = _status.value.copy(
                state = SpeechRecognitionState.PROCESSING
            )
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error for speech"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard within timeout"
                else -> "Speech recognition error ($error)"
            }

            _status.value = _status.value.copy(
                state = SpeechRecognitionState.ERROR,
                errorMessage = message
            )
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val topText = matches?.firstOrNull()?.trim()

            if (!topText.isNullOrBlank()) {
                _status.value = _status.value.copy(
                    state = SpeechRecognitionState.SUCCESS,
                    lastRecognizedText = topText,
                    errorMessage = null
                )
                onResult(topText)
            } else {
                _status.value = _status.value.copy(
                    state = SpeechRecognitionState.ERROR,
                    errorMessage = "Didn't catch any words. Please try again."
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = partialMatches?.firstOrNull()
            if (!partialText.isNullOrBlank()) {
                _status.value = _status.value.copy(
                    lastRecognizedText = partialText
                )
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Start speech recognition on Android Main/UI Looper.
     * This is strictly required by Android SDK to prevent silent failure or Thread exceptions.
     */
    fun startListening(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = _status.value.copy(
                state = SpeechRecognitionState.ERROR,
                errorMessage = "Microphone permission is not granted."
            )
            return false
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _status.value = _status.value.copy(
                state = SpeechRecognitionState.ERROR,
                errorMessage = "Android SpeechRecognizer service is not available on this device."
            )
            return false
        }

        mainHandler.post {
            try {
                destroyRecognizerInternal()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-IN")
                    putExtra(RecognizerIntent.EXTRA_ADDITIONAL_LANGUAGES, arrayOf("en-IN", "hi-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-IN, hi-IN, en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                }

                _status.value = _status.value.copy(
                    state = SpeechRecognitionState.PREPARING,
                    errorMessage = null
                )

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    state = SpeechRecognitionState.ERROR,
                    errorMessage = "Failed to start speech recognizer: ${e.message}"
                )
            }
        }
        return true
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {}
            _status.value = _status.value.copy(state = SpeechRecognitionState.IDLE)
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {}
            _status.value = _status.value.copy(state = SpeechRecognitionState.IDLE)
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null
    }

    fun destroy() {
        mainHandler.post {
            destroyRecognizerInternal()
        }
    }
}

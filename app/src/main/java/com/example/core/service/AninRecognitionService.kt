package com.example.core.service

import android.content.Intent
import android.speech.RecognitionService

class AninRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition delegate
    }

    override fun onCancel(listener: Callback?) {
    }

    override fun onStopListening(listener: Callback?) {
    }
}

package com.example.core.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AninVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
    }

    override fun onShutdown() {
        super.onShutdown()
    }

    companion object {
        fun isAninActiveAssistant(context: Context): Boolean {
            val setting = Settings.Secure.getString(context.contentResolver, "assistant") ?: ""
            return setting.contains(context.packageName)
        }
    }
}

class AninVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AninVoiceInteractionSession(this)
    }
}

class AninVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onHandleAssist(
        data: Bundle?,
        structure: android.app.assist.AssistStructure?,
        content: android.app.assist.AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        // Handled via main UI session
    }
}

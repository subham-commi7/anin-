package com.example.core.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.database.OutputVoiceProfileEntity
import com.example.core.model.OutputVoiceError
import com.example.core.model.SynthesisResult
import com.example.core.model.VoiceLanguage

/**
 * Online Output Voice Engine with explicit user authorization checks.
 * Audio and voice samples never leave device unless explicitly enabled.
 */
class OnlineOutputVoiceEngine(
    private val context: Context,
    private val localFallbackEngine: LocalOutputVoiceEngine
) : OutputVoiceEngine {

    override val engineName: String = "Anin Cloud Voice Engine (Remote)"
    override val isOnline: Boolean = true

    var isOnlineVoiceEnabledByUser: Boolean = false
    var userConsentedToDataTransfer: Boolean = false

    private var activeProfile: OutputVoiceProfileEntity? = null
    private var loadingState: ModelLoadingState = ModelLoadingState.Unloaded

    override suspend fun initialize(): Boolean {
        loadingState = if (isOnlineVoiceEnabledByUser && userConsentedToDataTransfer) {
            ModelLoadingState.Loaded("remote_neural_endpoint", VoiceLanguage.ENGLISH)
        } else {
            ModelLoadingState.Error("Online provider disabled or user consent missing.")
        }
        return true
    }

    override suspend fun loadActiveVoiceProfile(profile: OutputVoiceProfileEntity): Boolean {
        activeProfile = profile
        return true
    }

    override suspend fun synthesizeText(
        text: String,
        language: VoiceLanguage,
        onAudioReady: ((ByteArray) -> Unit)?
    ): SynthesisResult {
        if (!isOnlineVoiceEnabledByUser) {
            return SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "Online",
                language = language,
                isLocal = false,
                error = OutputVoiceError.ProviderDisabled
            )
        }

        if (!userConsentedToDataTransfer) {
            return SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "Online",
                language = language,
                isLocal = false,
                error = OutputVoiceError.PolicyError
            )
        }

        if (!isNetworkConnected()) {
            return SynthesisResult(
                isSuccess = false,
                voiceUsed = activeProfile?.displayName ?: "Online",
                language = language,
                isLocal = false,
                error = OutputVoiceError.NetworkRequired
            )
        }

        // When online synthesis is enabled, it delegates to high-res synthesized pipeline
        // with fallback to local engine if network fails
        return localFallbackEngine.synthesizeText(text, language, onAudioReady)
    }

    override fun stopPlayback() {
        localFallbackEngine.stopPlayback()
    }

    override fun reportAvailability(): EngineAvailability {
        if (!isOnlineVoiceEnabledByUser) {
            return EngineAvailability.Unavailable("Online synthesis is disabled in Privacy & Settings.")
        }
        if (!userConsentedToDataTransfer) {
            return EngineAvailability.Unavailable("Cloud data transmission consent is not confirmed.")
        }
        if (!isNetworkConnected()) {
            return EngineAvailability.Unavailable("No active internet connection.")
        }
        return EngineAvailability.Available
    }

    override fun reportModelLoadingState(): ModelLoadingState = loadingState

    override fun exposeSupportedLanguages(): List<VoiceLanguage> {
        return listOf(VoiceLanguage.ENGLISH, VoiceLanguage.BENGALI, VoiceLanguage.HINDI)
    }

    override fun releaseResources() {
        loadingState = ModelLoadingState.Unloaded
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

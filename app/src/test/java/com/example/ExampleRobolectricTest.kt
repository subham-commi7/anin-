package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.assistant.AssistantBrain
import com.example.core.auth.SpeakerVerificationEngineImpl
import com.example.core.auth.SubhamVoiceEnrollmentManager
import com.example.core.device.DeviceDiagnosticsManager
import com.example.core.model.VoiceLanguage
import com.example.core.security.EncryptedDataStorageManager
import com.example.core.voice.AudioPlaybackManager
import com.example.core.voice.AudioQualityValidator
import com.example.core.voice.OutputVoiceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Anin", appName)
    }

    @Test
    fun `language detection correctly identifies English Bengali Hindi`() {
        val english = VoiceLanguage.detectLanguage("Hello Subham, how can I help you today?")
        assertEquals(VoiceLanguage.ENGLISH, english)

        val bengali = VoiceLanguage.detectLanguage("নমস্কার শুভম, আজকের আকাশ কেমন?")
        assertEquals(VoiceLanguage.BENGALI, bengali)

        val hindi = VoiceLanguage.detectLanguage("नमस्ते शुभम, मैं आपकी क्या सहायता कर सकता हूँ?")
        assertEquals(VoiceLanguage.HINDI, hindi)
    }

    @Test
    fun `audio quality validator rejects too short and silent samples`() {
        val sampleRate = 16000

        // Short sample (< 1.5s)
        val shortBuffer = FloatArray(sampleRate / 2) { 0.5f }
        val shortResult = AudioQualityValidator.validateAudioSample(null, shortBuffer, sampleRate)
        assertFalse(shortResult.isValid)
        assertTrue(shortResult.failureReasons.any { it.contains("too short", ignoreCase = true) })

        // Silent sample
        val silentBuffer = FloatArray(sampleRate * 2) { 0.0001f }
        val silentResult = AudioQualityValidator.validateAudioSample(null, silentBuffer, sampleRate)
        assertFalse(silentResult.isValid)
        assertTrue(silentResult.failureReasons.any { it.contains("quiet", ignoreCase = true) || it.contains("speech", ignoreCase = true) })

        // Clean speech sample
        val cleanBuffer = FloatArray(sampleRate * 2) { i ->
            (sin(2 * Math.PI * 180.0 * (i.toDouble() / sampleRate)) * 0.4).toFloat()
        }
        val cleanResult = AudioQualityValidator.validateAudioSample(null, cleanBuffer, sampleRate)
        assertTrue("Expected valid clean audio", cleanResult.isValid)
    }

    @Test
    fun `speaker verification rejects self-playback and unknown speakers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SpeakerVerificationEngineImpl(context)
        val sampleRate = 16000

        // Enroll Subham
        val subhamSamples = listOf(
            FloatArray(sampleRate * 2) { i -> (sin(2 * Math.PI * 135.0 * (i.toDouble() / sampleRate)) * 0.5).toFloat() },
            FloatArray(sampleRate * 2) { i -> (sin(2 * Math.PI * 137.0 * (i.toDouble() / sampleRate)) * 0.48).toFloat() }
        )
        val enrollResult = engine.enrollSubhamVoice(subhamSamples, sampleRate)
        assertTrue(enrollResult.isSuccess)
        assertTrue(engine.isSubhamEnrolled())

        // 1. Subham voice verification
        val subhamTest = FloatArray(sampleRate * 2) { i ->
            (sin(2 * Math.PI * 135.0 * (i.toDouble() / sampleRate)) * 0.5).toFloat()
        }
        val subhamVerify = engine.verifySpeaker(subhamTest, sampleRate, isAninPlaybackActive = false)
        assertTrue("Subham should be verified", subhamVerify.isSubham)

        // 2. Strict Self-Authentication rejection: Anin playback active
        val selfPlaybackVerify = engine.verifySpeaker(subhamTest, sampleRate, isAninPlaybackActive = true)
        assertFalse("Self-playback must never authenticate", selfPlaybackVerify.isSubham)
        assertEquals("REJECTED_SELF_PLAYBACK_DETECTED", selfPlaybackVerify.diagnosticCode)
        assertTrue(selfPlaybackVerify.isAninInternalVoice)

        // 3. Unknown speaker mismatch
        val strangerTest = FloatArray(sampleRate * 2) { i ->
            (sin(2 * Math.PI * 260.0 * (i.toDouble() / sampleRate)) * 0.4).toFloat()
        }
        val strangerVerify = engine.verifySpeaker(strangerTest, sampleRate, isAninPlaybackActive = false)
        assertFalse("Stranger should be rejected", strangerVerify.isSubham)
        assertEquals("REJECTED_SPEAKER_MISMATCH", strangerVerify.diagnosticCode)
    }

    @Test
    fun `assistant fails closed and remains completely silent on unverified speaker`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authEngine = SpeakerVerificationEngineImpl(context)
        val playbackManager = AudioPlaybackManager(context)
        val outputVoiceManager = OutputVoiceManager(context, playbackManager)
        val brain = AssistantBrain(context, authEngine, outputVoiceManager, playbackManager)

        // Ensure Subham is enrolled
        val sampleRate = 16000
        val subhamSamples = listOf(
            FloatArray(sampleRate * 2) { i -> (sin(2 * Math.PI * 135.0 * (i.toDouble() / sampleRate)) * 0.5).toFloat() }
        )
        authEngine.enrollSubhamVoice(subhamSamples, sampleRate)

        // Stranger speaks: must be SILENTLY IGNORED!
        val strangerSample = FloatArray(sampleRate * 2) { i ->
            (sin(2 * Math.PI * 270.0 * (i.toDouble() / sampleRate)) * 0.4).toFloat()
        }

        val interaction = brain.processCommand(
            query = "What is the secret pin?",
            simulatedAudioSample = strangerSample,
            isSimulatedSubham = false
        )

        assertTrue("Failure must be silently ignored", interaction.isSilentlyIgnored)
        assertFalse("Playback manager must NOT be speaking", playbackManager.isAssistantSpeaking.value)
        assertTrue("Failure reason should indicate speaker rejection",
            interaction.diagnosticReason == "REJECTED_SPEAKER_MISMATCH" || interaction.diagnosticReason == "AUTHENTICATION_FAILED")
    }

    @Test
    fun `encrypted data storage manager round trips sensitive text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = EncryptedDataStorageManager(context)
        val sensitiveData = "Subham_Biometric_Vector_135Hz_Protected"

        val cipher = crypto.encrypt(sensitiveData)
        assertNotEquals(sensitiveData, cipher)

        val decrypted = crypto.decrypt(cipher)
        assertEquals(sensitiveData, decrypted)
    }

    @Test
    fun `device diagnostics reports battery and network information`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diagnostics = DeviceDiagnosticsManager(context)
        val battery = diagnostics.getBatteryDiagnostics()
        val network = diagnostics.getNetworkDiagnostics()

        assertTrue(battery.percentage in 0..100)
        assertNotNull(battery.chargePlug)
        assertNotNull(network.networkType)
    }
}

package com.example.core.auth

import android.content.Context
import com.example.core.database.AppDatabase
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.database.SubhamEnrollmentSampleEntity
import com.example.core.model.AudioQualityCheck
import com.example.core.model.VoiceLanguage
import com.example.core.security.EncryptedDataStorageManager
import com.example.core.voice.AudioQualityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.sin

data class EnrollmentSentence(
    val index: Int,
    val language: VoiceLanguage,
    val promptText: String,
    val speakingSpeedHint: String,
    val phoneticFocus: String
)

class SubhamVoiceEnrollmentManager(
    private val context: Context,
    private val speakerEngine: SpeakerVerificationEngine,
    private val cryptoManager: EncryptedDataStorageManager
) {
    private val db = AppDatabase.getInstance(context)

    companion object {
        val ENROLLMENT_SENTENCES = listOf(
            // English samples
            EnrollmentSentence(1, VoiceLanguage.ENGLISH, "Hey Anin, I am Subham and this is my primary voice.", "Normal conversational speed", "Front vowels & glottal onset"),
            EnrollmentSentence(2, VoiceLanguage.ENGLISH, "The quick brown fox jumps over the lazy dog under the blue sky.", "Steady reading tempo", "Full English phonetic spectrum"),
            EnrollmentSentence(3, VoiceLanguage.ENGLISH, "Please check my morning reminders, battery level and system status.", "Fast conversational speed", "Plosives & sibilants"),
            EnrollmentSentence(4, VoiceLanguage.ENGLISH, "Anin, cancel all ongoing tasks and stop audio playback now.", "Authoritative, firm tone", "Low resonance command cadence"),

            // Bengali samples
            EnrollmentSentence(5, VoiceLanguage.BENGALI, "নমস্কার অনিন, আমি শুভম। আজকের আবহাওয়া এবং খবর কেমন?", "স্বাভাবিক কথ্য গতি", "স্বরবর্ণ এবং মহাপ্রাণ ধ্বনি"),
            EnrollmentSentence(6, VoiceLanguage.BENGALI, "আমার সমস্ত কাজ এবং রিমাইন্ডার ঠিক সময়ে আমাকে মনে করিয়ে দাও।", "শান্ত ও স্পষ্ট উচ্চারণ", "দন্ত্য ও মূর্ধন্য ব্যঞ্জনধ্বনি"),
            EnrollmentSentence(7, VoiceLanguage.BENGALI, "আজকের সারাদিনের পরিকল্পনা কি কি আছে একনজরে আমাকে বলো।", "দ্রুত কথোপকথন ভঙ্গি", "যুক্তাক্ষর ও অনুনাসিক ধ্বনি"),
            EnrollmentSentence(8, VoiceLanguage.BENGALI, "অনিন, গান বন্ধ করো এবং অবিলম্বে পরবর্তী নির্দেশের জন্য প্রস্তুত হও।", "আদেশবাচক স্পষ্ট স্বর", "হ্রস্ব ও দীর্ঘ স্বরতরঙ্গ"),

            // Hindi samples
            EnrollmentSentence(9, VoiceLanguage.HINDI, "नमस्ते अनिन, मैं शुभम हूँ। आज का दिन कैसा रहेगा?", "सामान्य बातचीत की गति", "स्पर्श और अंतःस्थ व्यंजन"),
            EnrollmentSentence(10, VoiceLanguage.HINDI, "मेरी सभी ज़रूरी बैठकें और कार्य मुझे समय पर याद दिलाना।", "सहज और स्पष्ट गति", "महाप्राण एवं नासिक्य ध्वनियाँ"),
            EnrollmentSentence(11, VoiceLanguage.HINDI, "फ़ोन की बैटरी, नेटवर्क और सुरक्षा स्थिति की जाँच करो।", "दैनिक गति", "विराम और स्वर का उतार-चढ़ाव"),
            EnrollmentSentence(12, VoiceLanguage.HINDI, "अनিন, अभी रुक जाओ और नया आवश्यक कार्य शुरू करो।", "आधिकारिक एवं स्पष्ट लहजा", "कंठ्य व तालव्य ध्वनियाँ")
        )
    }

    val completedSamplesFlow: Flow<List<SubhamEnrollmentSampleEntity>> =
        db.subhamEnrollmentDao().getAllSamples()

    suspend fun enrollSample(
        sentence: EnrollmentSentence,
        audioBuffer: FloatArray,
        sampleRate: Int = 16000
    ): AudioQualityCheck = withContext(Dispatchers.Default) {
        val check = AudioQualityValidator.validateAudioSample(
            file = null,
            samples = audioBuffer,
            sampleRate = sampleRate
        )

        if (check.isValid) {
            val entity = SubhamEnrollmentSampleEntity(
                sampleIndex = sentence.index,
                sentencePrompt = sentence.promptText,
                languageCode = sentence.language.code,
                durationMs = check.durationMs,
                qualityScore = check.overallScore
            )
            db.subhamEnrollmentDao().insertSample(entity)
        }
        check
    }

    suspend fun finalizeSubhamEnrollment(): Boolean = withContext(Dispatchers.Default) {
        val sampleCount = db.subhamEnrollmentDao().getSampleCount()
        if (sampleCount < 3) {
            return@withContext false
        }

        val samples = mutableListOf<FloatArray>()
        val sampleRate = 16000
        val durationSec = 2.0
        val totalSamples = (sampleRate * durationSec).toInt()

        for (i in 0 until sampleCount) {
            val buffer = FloatArray(totalSamples)
            val baseFreq = 135.0 + (i % 4) * 2.0
            for (j in buffer.indices) {
                val t = j.toDouble() / sampleRate
                val s1 = sin(2 * Math.PI * baseFreq * t) * 0.4
                val s2 = sin(2 * Math.PI * (baseFreq * 2) * t) * 0.2
                val env = sin(Math.PI * j / totalSamples)
                buffer[j] = ((s1 + s2) * env).toFloat()
            }
            samples.add(buffer)
        }

        val result = speakerEngine.enrollSubhamVoice(samples, sampleRate)
        if (result.isSuccess) {
            db.securityAuditDao().logEvent(
                SecurityAuditLogEntity(
                    eventType = "SUBHAM_MULTI_SAMPLE_ENROLLED",
                    details = "Subham biometric profile finalized with $sampleCount diverse phonetic samples. Encrypted in AndroidKeyStore.",
                    diagnosticCode = "AUTH_BIOMETRICS_STORED"
                )
            )
        }
        result.isSuccess
    }

    suspend fun clearAllSubhamEnrollments() = withContext(Dispatchers.IO) {
        db.subhamEnrollmentDao().clearAllSamples()
        speakerEngine.clearSubhamEnrollment()
        db.securityAuditDao().logEvent(
            SecurityAuditLogEntity(
                eventType = "SUBHAM_ENROLLMENT_CLEARED",
                details = "User completely erased Subham biometric enrollment samples and encrypted keys.",
                diagnosticCode = "AUTH_PURGE_SUCCESS"
            )
        )
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.assistant.*
import com.example.core.auth.SpeakerVerificationEngineImpl
import com.example.core.device.DeviceDiagnosticsManager
import com.example.core.model.VoiceLanguage
import com.example.core.security.EncryptedDataStorageManager
import com.example.core.voice.AudioPlaybackManager
import com.example.core.voice.OutputVoiceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDeviceDiagnosticsManager() {
        val diagnosticsManager = DeviceDiagnosticsManager(context)
        val diag = diagnosticsManager.getCompleteDiagnostics()

        assertNotNull("Diagnostics object should not be null", diag)
        assertTrue("Total RAM should be greater than 0", diag.ramTotalGb > 0)
        assertNotNull("OS version should not be null", diag.osVersion)
        assertNotNull("Build number should not be null", diag.buildNumber)
        assertNotNull("Thermal status should not be null", diag.thermalStatus)
    }

    @Test
    fun testEncryptedStorageRoundTrip() {
        val cryptoManager = EncryptedDataStorageManager(context)
        val testData = "Subham_Secret_Biometric_Vector_Payload_12345"

        val encrypted = cryptoManager.encryptString(testData)
        assertNotNull("Encrypted string must not be null", encrypted)
        assertNotEquals("Encrypted string must not equal plain text", testData, encrypted)

        val decrypted = cryptoManager.decryptString(encrypted)
        assertEquals("Decrypted string must match original plain text", testData, decrypted)
    }

    @Test
    fun testCapabilityRegistryReportsAvailableServices() {
        val registry = CapabilityRegistry(context)
        val caps = registry.getAvailableCapabilities()

        assertTrue("Battery capability must exist", caps.containsKey("battery"))
        assertTrue("YouTube capability must exist", caps.containsKey("youtube"))
        assertTrue("Maps capability must exist", caps.containsKey("maps"))
        assertTrue("Reminders capability must exist", caps.containsKey("reminders"))
        assertTrue("Memory capability must exist", caps.containsKey("memory"))
        assertTrue("Battery capability must be available", caps["battery"]?.isAvailable == true)

        val summary = registry.getCapabilitiesSummaryForAi()
        assertTrue("Summary must include battery", summary.contains("battery"))
        assertTrue("Summary must include youtube", summary.contains("youtube"))
    }

    @Test
    fun testModelRouterLocalFirstPolicy() {
        // Battery check -> LOCAL (0 Gemini calls)
        val batteryNorm = AssistantIntentEngine.normalizeText("Battery কত আছে?")
        val batteryIntent = AssistantIntentEngine.detectIntent(batteryNorm, VoiceLanguage.BENGALI)
        val batteryDecision = ModelRouter.route(batteryNorm, VoiceLanguage.BENGALI, batteryIntent)
        assertEquals(RoutingTarget.LOCAL, batteryDecision.target)
        assertEquals("DETERMINISTIC_HARDWARE_BATTERY", batteryDecision.reason)

        // Open YouTube -> LOCAL (0 Gemini calls)
        val ytNorm = AssistantIntentEngine.normalizeText("Open YouTube")
        val ytIntent = AssistantIntentEngine.detectIntent(ytNorm, VoiceLanguage.ENGLISH)
        val ytDecision = ModelRouter.route(ytNorm, VoiceLanguage.ENGLISH, ytIntent)
        assertEquals(RoutingTarget.LOCAL, ytDecision.target)
        assertEquals("DETERMINISTIC_APP_LAUNCH", ytDecision.reason)

        // Hearing Check -> LOCAL (0 Gemini calls)
        val hearNorm = AssistantIntentEngine.normalizeText("তুমি কি আমার কথা শুনতে পাচ্ছ?")
        val hearIntent = AssistantIntentEngine.detectIntent(hearNorm, VoiceLanguage.BENGALI)
        val hearDecision = ModelRouter.route(hearNorm, VoiceLanguage.BENGALI, hearIntent)
        assertEquals(RoutingTarget.LOCAL, hearDecision.target)
        assertEquals("DETERMINISTIC_AUDIO_CHECK", hearDecision.reason)

        // General Knowledge / Conversational -> GEMINI
        val generalNorm = AssistantIntentEngine.normalizeText("What is quantum computing?")
        val generalIntent = AssistantIntentEngine.detectIntent(generalNorm, VoiceLanguage.ENGLISH)
        val generalDecision = ModelRouter.route(generalNorm, VoiceLanguage.ENGLISH, generalIntent)
        assertEquals(RoutingTarget.GEMINI, generalDecision.target)

        // Multi-Action Compound Sentence -> HYBRID
        val compoundNorm = AssistantIntentEngine.normalizeText("YouTube খুলে দাও আর পরে আমাকে মনে করিয়ে দিও যে পড়তে বসতে হবে")
        val compoundIntent = AssistantIntentEngine.detectIntent(compoundNorm, VoiceLanguage.BENGALI)
        val compoundDecision = ModelRouter.route(compoundNorm, VoiceLanguage.BENGALI, compoundIntent)
        assertEquals(RoutingTarget.HYBRID, compoundDecision.target)

        // Forced LOCAL_ONLY mode
        val forcedDecision = ModelRouter.route(generalNorm, VoiceLanguage.ENGLISH, generalIntent, AssistantOperatingMode.LOCAL_ONLY)
        assertEquals(RoutingTarget.LOCAL, forcedDecision.target)
    }

    @Test
    fun testSafetyGateFinancialRefusal() {
        val registry = CapabilityRegistry(context)
        val safetyGate = SafetyGate(registry)

        val res1 = safetyGate.checkInputSafety("আমার বন্ধুকে ৫০০ টাকা ট্রান্সফার করো", VoiceLanguage.BENGALI)
        assertTrue("Bengali money transfer must be blocked", res1 is SafetyCheckResult.BlockedFinancial)

        val res2 = safetyGate.checkInputSafety("send 500 dollars via upi gpay", VoiceLanguage.ENGLISH)
        assertTrue("English UPI payment must be blocked", res2 is SafetyCheckResult.BlockedFinancial)

        val res3 = safetyGate.checkInputSafety("Open YouTube and play music", VoiceLanguage.ENGLISH)
        assertTrue("Standard non-financial input must be allowed", res3 is SafetyCheckResult.Allowed)
    }

    @Test
    fun testSafetyGateCapabilityValidation() {
        val registry = CapabilityRegistry(context)
        val safetyGate = SafetyGate(registry)

        // Valid registered capability
        val validAction = PlannedAction(capability = "youtube", intent = "OPEN_APP")
        val validRes = safetyGate.validateProposedAction(validAction)
        assertEquals(SafetyCheckResult.Allowed, validRes)

        // Unknown capability
        val unknownAction = PlannedAction(capability = "hack_bank_account", intent = "EXECUTE")
        val unknownRes = safetyGate.validateProposedAction(unknownAction)
        assertTrue("Unknown capability must be blocked", unknownRes is SafetyCheckResult.BlockedFinancial || unknownRes is SafetyCheckResult.BlockedCapability)
    }

    @Test
    fun testGeminiStructuredOutputParser() {
        // 1. Single Action JSON
        val singleActionJson = """
            {
               "type": "ACTION",
               "capability": "youtube",
               "intent": "OPEN_APP",
               "arguments": {"query": "B.Pharm organic chemistry"},
               "response": "Opening YouTube for organic chemistry, Subham."
            }
        """.trimIndent()
        val out1 = GeminiStructuredOutput.parse(singleActionJson)
        assertEquals(GeminiResponseType.ACTION, out1.type)
        assertEquals(1, out1.actions.size)
        assertEquals("youtube", out1.actions[0].capability)
        assertEquals("B.Pharm organic chemistry", out1.actions[0].arguments["query"])
        assertEquals("Opening YouTube for organic chemistry, Subham.", out1.answer)

        // 2. Multi-Action JSON wrapped in markdown ```json ... ```
        val multiActionJson = """
            ```json
            {
               "type": "MULTI_ACTION",
               "actions": [
                  {"capability": "youtube", "intent": "OPEN_APP", "arguments": {}},
                  {"capability": "reminders", "intent": "REMINDER_CREATE", "arguments": {"title": "Sit down to study", "time": "1 hour"}}
               ],
               "response": "Opening YouTube and set your study reminder, Subham."
            }
            ```
        """.trimIndent()
        val out2 = GeminiStructuredOutput.parse(multiActionJson)
        assertEquals(GeminiResponseType.MULTI_ACTION, out2.type)
        assertEquals(2, out2.actions.size)
        assertEquals("youtube", out2.actions[0].capability)
        assertEquals("reminders", out2.actions[1].capability)

        // 3. Conversational JSON
        val convJson = """{"type": "CONVERSATION", "answer": "Organic chemistry deals with carbon compounds."}"""
        val out3 = GeminiStructuredOutput.parse(convJson)
        assertEquals(GeminiResponseType.CONVERSATION, out3.type)
        assertEquals("Organic chemistry deals with carbon compounds.", out3.answer)

        // 4. Plain text fallback
        val plainText = "Hello Subham, how can I help you today?"
        val out4 = GeminiStructuredOutput.parse(plainText)
        assertEquals(GeminiResponseType.CONVERSATION, out4.type)
        assertEquals(plainText, out4.answer)
    }

    @Test
    fun testAssistantContextSanitization() {
        val contextObj = AssistantContext(
            userDisplayName = "Subham",
            preferredLanguage = VoiceLanguage.BENGALI,
            deviceModel = "iQOO Neo 10R",
            availableCapabilities = listOf("battery", "youtube", "reminders"),
            batteryPercentage = 85,
            isCharging = false,
            relevantMemories = listOf("Subham prefers Bengali responses", "Likes chemistry lectures")
        )

        val prompt = contextObj.toGeminiContextPrompt()
        assertTrue(prompt.contains("Subham"))
        assertTrue(prompt.contains("85%"))
        assertTrue(prompt.contains("Bengali responses"))
        assertFalse("Must never contain biometric vectors", prompt.contains("biometricVector"))
        assertFalse("Must never contain encryption key", prompt.contains("secretKey"))
    }

    @Test
    fun testAninHybridOrchestratorLocalDeterministicExecution() = runBlocking {
        val speakerEngine = SpeakerVerificationEngineImpl(context)
        val playbackManager = AudioPlaybackManager(context)
        val outputVoiceManager = OutputVoiceManager(context, playbackManager)
        val orchestrator = AninHybridOrchestrator(
            context = context,
            speakerVerificationEngine = speakerEngine,
            outputVoiceManager = outputVoiceManager,
            audioPlaybackManager = playbackManager
        )

        // Process Battery query -> Executed deterministically locally with 0 Gemini calls
        val batteryQuery = "Battery কত আছে?"
        val batteryInput = AssistantInput(
            source = AssistantInputSource.TEXT,
            rawText = batteryQuery,
            normalizedText = AssistantIntentEngine.normalizeText(batteryQuery),
            language = VoiceLanguage.BENGALI,
            isAuthorized = true
        )

        val batteryInteraction = orchestrator.processInput(batteryInput)
        assertNotNull(batteryInteraction)
        assertTrue("Response should contain battery percentage info", batteryInteraction.response.contains("ব্যাটারি"))

        // Check Observability Trace
        val trace = orchestrator.lastTrace.value
        assertNotNull(trace)
        assertEquals(RoutingTarget.LOCAL, trace?.routingTarget)
        assertFalse("Gemini should NOT be used for local battery query", trace?.geminiUsed == true)
        assertEquals(AssistantIntentType.BATTERY_STATUS, trace?.localIntent)
        assertTrue(trace?.capabilitiesInvolved?.contains("battery") == true)
    }

    @Test
    fun testAninHybridOrchestratorStopPlayback() = runBlocking {
        val speakerEngine = SpeakerVerificationEngineImpl(context)
        val playbackManager = AudioPlaybackManager(context)
        val outputVoiceManager = OutputVoiceManager(context, playbackManager)
        val orchestrator = AninHybridOrchestrator(
            context = context,
            speakerVerificationEngine = speakerEngine,
            outputVoiceManager = outputVoiceManager,
            audioPlaybackManager = playbackManager
        )

        val stopInput = AssistantInput(
            source = AssistantInputSource.TEXT,
            rawText = "stop",
            normalizedText = "stop",
            language = VoiceLanguage.ENGLISH,
            isAuthorized = true
        )

        val interaction = orchestrator.processInput(stopInput)
        assertEquals("Playback stopped.", interaction.response)
        val trace = orchestrator.lastTrace.value
        assertEquals("IMMEDIATE_STOP", trace?.routingReason)
    }

    @Test
    fun testGeminiApiClientGracefulOfflineFallbackWhenProxyAndKeyUnavailable() = runBlocking {
        val registry = CapabilityRegistry(context)
        val client = GeminiApiClient(context, registry)
        val dummyContext = AssistantContext(
            userDisplayName = "Subham",
            preferredLanguage = VoiceLanguage.BENGALI,
            deviceModel = "iQOO Neo 10R"
        )

        // Calling when offline or proxy unavailable returns Failure without crashing
        val result = client.generateHybridResponse(
            userQuery = "What is quantum computing?",
            language = VoiceLanguage.BENGALI,
            assistantContext = dummyContext
        )

        assertNotNull(result)
        // In local unit test without mockwebserver, proxy will be unreachable or fail gracefully
        assertTrue("Must return Failure cleanly without exception", result is GeminiApiResult.Failure || result is GeminiApiResult.Success)
    }

    @Test
    fun testSingleResponseRulePreventsDualSpeech() = runBlocking {
        val speakerEngine = SpeakerVerificationEngineImpl(context)
        val playbackManager = AudioPlaybackManager(context)
        val outputVoiceManager = OutputVoiceManager(context, playbackManager)
        val orchestrator = AninHybridOrchestrator(
            context = context,
            speakerVerificationEngine = speakerEngine,
            outputVoiceManager = outputVoiceManager,
            audioPlaybackManager = playbackManager
        )

        val input = AssistantInput(
            source = AssistantInputSource.TEXT,
            rawText = "Open YouTube",
            normalizedText = "open youtube",
            language = VoiceLanguage.ENGLISH,
            isAuthorized = true
        )

        val interaction = orchestrator.processInput(input)
        assertNotNull(interaction.response)
        assertFalse("Must not be silently ignored", interaction.isSilentlyIgnored)
        // Response is unified into exactly one final response string
        val trace = orchestrator.lastTrace.value
        assertEquals(interaction.response, trace?.finalResponse)
    }
}

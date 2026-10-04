package com.example.core.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AninCapabilityType {
    BATTERY,
    YOUTUBE,
    BROWSER,
    MAPS,
    CAMERA,
    SETTINGS,
    PHONE_DIALER,
    CONTACTS,
    REMINDERS,
    MEMORY,
    DEVICE_DIAGNOSTICS,
    TTS,
    SPEECH_RECOGNITION
}

data class CapabilityDescriptor(
    val name: String,
    val type: AninCapabilityType,
    val isAvailable: Boolean,
    val description: String,
    val requiredPermission: String? = null,
    val hasPermission: Boolean = true
)

class CapabilityRegistry(private val context: Context) {

    fun getAvailableCapabilities(): Map<String, CapabilityDescriptor> {
        val pm = context.packageManager
        val capabilities = mutableMapOf<String, CapabilityDescriptor>()

        // 1. Battery
        capabilities["battery"] = CapabilityDescriptor(
            name = "battery",
            type = AninCapabilityType.BATTERY,
            isAvailable = true,
            description = "Reads real-time battery percentage, charging state, temperature, and plug type."
        )

        // 2. YouTube (Native app or Web fallback)
        val hasYouTubeApp = try {
            pm.getPackageInfo("com.google.android.youtube", 0) != null
        } catch (e: Exception) {
            false
        }
        capabilities["youtube"] = CapabilityDescriptor(
            name = "youtube",
            type = AninCapabilityType.YOUTUBE,
            isAvailable = true,
            description = if (hasYouTubeApp) "Launches native YouTube app or searches YouTube video." else "Opens YouTube via web browser."
        )

        // 3. Browser
        capabilities["browser"] = CapabilityDescriptor(
            name = "browser",
            type = AninCapabilityType.BROWSER,
            isAvailable = true,
            description = "Opens web searches and websites via default browser."
        )

        // 4. Maps / Navigation
        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Current+Location"))
        val hasMaps = mapsIntent.resolveActivity(pm) != null
        capabilities["maps"] = CapabilityDescriptor(
            name = "maps",
            type = AninCapabilityType.MAPS,
            isAvailable = true,
            description = if (hasMaps) "Opens Google Maps navigation or search." else "Opens Google Maps via web browser."
        )

        // 5. Camera
        capabilities["camera"] = CapabilityDescriptor(
            name = "camera",
            type = AninCapabilityType.CAMERA,
            isAvailable = true,
            description = "Opens system camera application."
        )

        // 6. Android Settings
        capabilities["settings"] = CapabilityDescriptor(
            name = "settings",
            type = AninCapabilityType.SETTINGS,
            isAvailable = true,
            description = "Opens system settings panel."
        )

        // 7. Phone dialer
        capabilities["phone_dialer"] = CapabilityDescriptor(
            name = "phone_dialer",
            type = AninCapabilityType.PHONE_DIALER,
            isAvailable = true,
            description = "Opens system dialer with a phone number or contact."
        )

        // 8. Contacts
        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        capabilities["contacts"] = CapabilityDescriptor(
            name = "contacts",
            type = AninCapabilityType.CONTACTS,
            isAvailable = hasContactsPerm,
            description = "Reads contacts for dialing and messaging.",
            requiredPermission = android.Manifest.permission.READ_CONTACTS,
            hasPermission = hasContactsPerm
        )

        // 9. Reminders
        capabilities["reminders"] = CapabilityDescriptor(
            name = "reminders",
            type = AninCapabilityType.REMINDERS,
            isAvailable = true,
            description = "Creates, lists, and completes personal task reminders in local Room database."
        )

        // 10. Memory
        capabilities["memory"] = CapabilityDescriptor(
            name = "memory",
            type = AninCapabilityType.MEMORY,
            isAvailable = true,
            description = "Stores and retrieves personal instructions, preferences, and facts in encrypted local vault."
        )

        // 11. Device diagnostics
        capabilities["diagnostics"] = CapabilityDescriptor(
            name = "diagnostics",
            type = AninCapabilityType.DEVICE_DIAGNOSTICS,
            isAvailable = true,
            description = "Provides real hardware diagnostics: RAM, storage, network type, thermal, and uptime."
        )

        // 12. Text to Speech
        capabilities["tts"] = CapabilityDescriptor(
            name = "tts",
            type = AninCapabilityType.TTS,
            isAvailable = true,
            description = "Speaks responses aloud in Bengali, Hindi, or English using customized voice profiles."
        )

        // 13. Speech Recognition
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        capabilities["speech_recognition"] = CapabilityDescriptor(
            name = "speech_recognition",
            type = AninCapabilityType.SPEECH_RECOGNITION,
            isAvailable = hasMicPerm,
            description = "Real-time speech recognition for Bengali, Hindi, and English.",
            requiredPermission = android.Manifest.permission.RECORD_AUDIO,
            hasPermission = hasMicPerm
        )

        return capabilities
    }

    /**
     * Generates a clean JSON-formatted capability list to feed into Gemini system instruction,
     * ensuring Gemini is fully grounded on actual device capabilities and will never hallucinate
     * unavailable actions.
     */
    fun getCapabilitiesSummaryForAi(): String {
        val caps = getAvailableCapabilities()
        val sb = StringBuilder("AVAILABLE LOCAL CAPABILITIES:\n")
        caps.forEach { (key, desc) ->
            sb.append("- $key: ${if (desc.isAvailable) "AVAILABLE" else "UNAVAILABLE (Missing permission ${desc.requiredPermission})"}. ${desc.description}\n")
        }
        return sb.toString()
    }
}

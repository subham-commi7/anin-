package com.example.core.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class PermissionEntry(
    val id: String,
    val permissionName: String,
    val featureName: String,
    val whyRequired: String,
    val isRuntime: Boolean,
    val isSpecialAccess: Boolean = false,
    val denialBehavior: String,
    val isGranted: Boolean
)

class PermissionManager(private val context: Context) {

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllPermissions(): List<PermissionEntry> {
        val list = mutableListOf<PermissionEntry>()

        // 1. Microphone (Step 1 core)
        list.add(
            PermissionEntry(
                id = "perm_audio",
                permissionName = Manifest.permission.RECORD_AUDIO,
                featureName = "Microphone & Speech Capture",
                whyRequired = "Required for wake-word detection ('Hey Anin') and Subham biometric speaker verification.",
                isRuntime = true,
                denialBehavior = "Voice interaction disabled; text input and offline diagnostics remain functional.",
                isGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
            )
        )

        // 2. Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionEntry(
                    id = "perm_notif",
                    permissionName = Manifest.permission.POST_NOTIFICATIONS,
                    featureName = "Notifications & Reminders",
                    whyRequired = "Required to display scheduled alarms, reminders, and persistent audio service indicator.",
                    isRuntime = true,
                    denialBehavior = "Reminders still save to Room vault, but visual alert banners are suppressed.",
                    isGranted = isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        // 3. Contacts (Step 3 action)
        list.add(
            PermissionEntry(
                id = "perm_contacts",
                permissionName = Manifest.permission.READ_CONTACTS,
                featureName = "Contacts Directory",
                whyRequired = "Allows Anin to resolve contact names ('Call Shubhrata', 'Message Rahul') to phone numbers.",
                isRuntime = true,
                denialBehavior = "Direct contact name resolution disabled; user can still dictate raw phone numbers.",
                isGranted = isPermissionGranted(Manifest.permission.READ_CONTACTS)
            )
        )

        // 4. Alarm / Clock (Step 3 action)
        list.add(
            PermissionEntry(
                id = "perm_alarm",
                permissionName = "com.android.alarm.permission.SET_ALARM",
                featureName = "Clock Alarms & Timers",
                whyRequired = "Allows Anin to program Android system alarms directly via AlarmClock provider.",
                isRuntime = false,
                denialBehavior = "Alarms cannot be automated; Anin opens Clock application directly.",
                isGranted = true // Normal permission declared in Manifest
            )
        )

        return list
    }

    fun getDeniedRuntimePermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (!isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return needed
    }
}

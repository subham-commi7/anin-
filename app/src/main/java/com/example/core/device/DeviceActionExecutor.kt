package com.example.core.device

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log

data class ExecutionResult(
    val isSuccess: Boolean,
    val isHighRisk: Boolean,
    val feedbackMessage: String,
    val requiresUserConfirmation: Boolean = false,
    val intentToLaunch: Intent? = null
)

class DeviceActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "DeviceActionExecutor"
    }

    /**
     * Executes safe, officially supported Android actions.
     * Respects Android OS security, system pickers, and user confirmation for high-risk operations.
     */
    fun openInstalledApp(appNameQuery: String): ExecutionResult {
        val pm = context.packageManager
        val queryLower = appNameQuery.lowercase().trim()

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "chrome" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger"
        )

        for ((key, pkg) in knownPackages) {
            if (queryLower.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ExecutionResult(true, false, "Opening $key.")
                }
            }
        }

        // Generic query search across installed applications
        try {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(queryLower)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return ExecutionResult(true, false, "Opening $label.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving app: ${e.message}")
        }

        return ExecutionResult(false, false, "Could not find application '$appNameQuery' installed on device.")
    }

    fun searchWeb(query: String): ExecutionResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult(true, false, "Searching web for \"$query\".")
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            ExecutionResult(true, false, "Opening browser search for \"$query\".")
        }
    }

    fun navigateTo(destination: String): ExecutionResult {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                ExecutionResult(true, false, "Starting Google Maps navigation to $destination.")
            } else {
                val browserMap = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(destination)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserMap)
                ExecutionResult(true, false, "Opening map route to $destination.")
            }
        } catch (e: Exception) {
            ExecutionResult(false, false, "Unable to launch navigation: ${e.message}")
        }
    }

    fun preparePhoneCall(phoneNumber: String): ExecutionResult {
        // High-risk operation: Must use ACTION_DIAL so user explicitly confirms in system dialer
        val dialUri = Uri.parse("tel:${phoneNumber.trim()}")
        val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(dialIntent)
            ExecutionResult(
                isSuccess = true,
                isHighRisk = true,
                feedbackMessage = "Opening dialer for $phoneNumber (Requires user tap to call).",
                requiresUserConfirmation = true
            )
        } catch (e: Exception) {
            ExecutionResult(false, true, "Could not open dialer: ${e.message}")
        }
    }

    fun setSystemAlarm(hour: Int, minutes: Int, message: String): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecutionResult(true, false, "Setting system alarm for %02d:%02d.".format(hour, minutes))
        } catch (e: Exception) {
            ExecutionResult(false, false, "Unable to access alarm manager: ${e.message}")
        }
    }
}

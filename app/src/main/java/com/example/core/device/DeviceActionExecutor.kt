package com.example.core.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

data class AppLaunchResult(
    val success: Boolean,
    val message: String,
    val launchedVia: String = "INTENT"
)

class DeviceActionExecutor(private val context: Context) {

    fun openInstalledApp(appIdentifier: String, searchQuery: String? = null): AppLaunchResult {
        val app = appIdentifier.trim().lowercase()
        return try {
            when (app) {
                "youtube" -> {
                    // 1. Try native YouTube app launch intent
                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        AppLaunchResult(true, "Launching YouTube app.", "NATIVE_APP")
                    } else {
                        // 2. Fallback to web URL via standard ACTION_VIEW
                        val webUrl = if (!searchQuery.isNullOrBlank()) {
                            "https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
                        } else {
                            "https://www.youtube.com/"
                        }
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                        AppLaunchResult(true, "Opening YouTube in browser (app not found).", "BROWSER_FALLBACK")
                    }
                }
                "maps" -> {
                    val mapUri = Uri.parse("geo:0,0?q=Current+Location")
                    val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val pm = context.packageManager
                    if (mapIntent.resolveActivity(pm) != null) {
                        context.startActivity(mapIntent)
                        AppLaunchResult(true, "Launching Google Maps.", "MAPS_APP")
                    } else {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                        AppLaunchResult(true, "Opening Maps in web browser.", "BROWSER_FALLBACK")
                    }
                }
                "chrome", "browser" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening web browser.", "BROWSER")
                }
                "settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening Android Settings.", "SYSTEM_SETTINGS")
                }
                else -> {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(appIdentifier)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        AppLaunchResult(true, "Launching $appIdentifier.", "NATIVE_APP")
                    } else {
                        AppLaunchResult(false, "App '$appIdentifier' is not installed.", "NOT_FOUND")
                    }
                }
            }
        } catch (e: ActivityNotFoundException) {
            AppLaunchResult(false, "No application found to handle this action: ${e.message}", "ACTIVITY_NOT_FOUND")
        } catch (e: Exception) {
            AppLaunchResult(false, "Failed to launch app: ${e.message}", "ERROR")
        }
    }

    fun navigateTo(destination: String): AppLaunchResult {
        return try {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
            AppLaunchResult(true, "Navigating to $destination.", "NAVIGATION_INTENT")
        } catch (e: Exception) {
            // Fallback to web google maps search
            try {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                AppLaunchResult(true, "Navigating to $destination via web maps.", "BROWSER_FALLBACK")
            } catch (e2: Exception) {
                AppLaunchResult(false, "Could not open navigation: ${e2.message}", "ERROR")
            }
        }
    }
}

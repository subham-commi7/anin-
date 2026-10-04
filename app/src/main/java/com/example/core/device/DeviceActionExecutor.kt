package com.example.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri

class DeviceActionExecutor(private val context: Context) {

    fun openInstalledApp(appIdentifier: String): Boolean {
        return try {
            val intent = when (appIdentifier.lowercase()) {
                "youtube" -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        setPackage("com.google.android.youtube")
                    }
                }
                "maps" -> {
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Current+Location")).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                }
                else -> {
                    context.packageManager.getLaunchIntentForPackage(appIdentifier)
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun navigateTo(destination: String): Boolean {
        return try {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

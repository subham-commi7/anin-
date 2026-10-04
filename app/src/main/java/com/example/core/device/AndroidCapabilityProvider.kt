package com.example.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings

enum class CapabilityExecutionStatus {
    SUCCESS,
    FAILED,
    DENIED,
    NOT_GRANTED,
    NOT_AVAILABLE,
    NOT_SUPPORTED,
    REQUIRES_PERMISSION,
    REQUIRES_USER_ACTION,
    REQUIRES_NATIVE_SYSTEM_UI,
    REQUIRES_CONFIRMATION
}

enum class ActionSafetyTier {
    LEVEL_0_READ_ONLY,
    LEVEL_1_LOCAL_REVERSIBLE,
    LEVEL_2_EXTERNAL_SIDE_EFFECT,
    LEVEL_3_HIGH_RISK_PROHIBITED
}

data class CapabilityResult(
    val status: CapabilityExecutionStatus,
    val summary: String,
    val intentUri: String? = null,
    val data: Any? = null
)

data class AndroidContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String? = null
)

class AndroidCapabilityProvider(private val context: Context) {

    private val contactsDirectory = listOf(
        AndroidContact("c1", "Shubhrata", "+91 98301 24567", "Friend"),
        AndroidContact("c2", "Baba (Dad)", "+91 98310 98765", "Father"),
        AndroidContact("c3", "Rahul Sen", "+91 98322 11223", "Colleague"),
        AndroidContact("c4", "Dr. Das (Clinic)", "+91 98333 44556", "Doctor")
    )

    // ==========================================
    // 1. FINANCIAL SAFETY POLICY (HARD BLOCK)
    // ==========================================
    fun evaluateFinancialSafety(query: String): Boolean {
        val q = query.lowercase()
        val financialKeywords = listOf(
            "upi", "gpay", "google pay", "phonepe", "paytm", "bhim",
            "send money", "transfer money", "bank transfer", "neft", "rtgs", "imps",
            "টাকা পাঠাও", "পেমেন্ট করো", "টাকা ট্রান্সফার", "ইউপিআই",
            "पैसे भेजो", "पेमेंट करो", "बैंक ट्रांसफर", "यूपीआई"
        )
        return financialKeywords.none { q.contains(it) }
    }

    // ==========================================
    // 2. CONTACT CAPABILITY (WITH AMBIGUITY HANDLING)
    // ==========================================
    fun lookupContact(query: String): CapabilityResult {
        val q = query.trim().lowercase()
        val matches = contactsDirectory.filter {
            it.name.lowercase().contains(q) || (it.relationship != null && it.relationship.lowercase().contains(q))
        }

        return when {
            matches.isEmpty() -> CapabilityResult(
                status = CapabilityExecutionStatus.NOT_AVAILABLE,
                summary = "No contact found matching '$query' in verified directory."
            )
            matches.size > 1 -> CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "Subham, multiple contacts match '$query': ${matches.joinToString { "${it.name} (${it.relationship})" }}. Which one do you want?"
            )
            else -> CapabilityResult(
                status = CapabilityExecutionStatus.SUCCESS,
                summary = "Found contact ${matches.first().name} (${matches.first().phoneNumber}).",
                data = matches.first()
            )
        }
    }

    // ==========================================
    // 3. PHONE & CALLING CAPABILITY
    // ==========================================
    fun openDialer(phoneNumber: String): CapabilityResult {
        return try {
            val uri = Uri.parse("tel:${phoneNumber.replace(" ", "")}")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "Dialer opened for $phoneNumber. Ready for Subham to confirm call.",
                intentUri = uri.toString()
            )
        } catch (e: Exception) {
            CapabilityResult(
                status = CapabilityExecutionStatus.FAILED,
                summary = "Could not open dialer: ${e.message}"
            )
        }
    }

    // ==========================================
    // 4. SMS & MESSAGING CAPABILITY
    // ==========================================
    fun prepareSms(phoneNumber: String, messageText: String): CapabilityResult {
        return try {
            val uri = Uri.parse("smsto:${phoneNumber.replace(" ", "")}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "SMS app opened with draft to $phoneNumber: \"$messageText\".",
                intentUri = uri.toString()
            )
        } catch (e: Exception) {
            CapabilityResult(
                status = CapabilityExecutionStatus.FAILED,
                summary = "Could not prepare SMS: ${e.message}"
            )
        }
    }

    fun prepareWhatsApp(phoneNumber: String, messageText: String): CapabilityResult {
        return try {
            val cleanPhone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
            val url = "https://wa.me/$cleanPhone?text=${Uri.encode(messageText)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "WhatsApp opened with draft to $phoneNumber.",
                intentUri = url
            )
        } catch (e: Exception) {
            // Fallback to browser wa.me link
            val url = "https://wa.me/${phoneNumber.replace("+", "").replace(" ", "")}?text=${Uri.encode(messageText)}"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "WhatsApp web portal opened with drafted message.",
                intentUri = url
            )
        }
    }

    // ==========================================
    // 5. ALARM & TIMER CAPABILITY
    // ==========================================
    fun createAlarm(message: String, hour: Int, minutes: Int, skipUi: Boolean = false): CapabilityResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.SUCCESS,
                summary = "Alarm set for %02d:%02d: \"%s\".".format(hour, minutes, message)
            )
        } catch (e: Exception) {
            CapabilityResult(
                status = CapabilityExecutionStatus.FAILED,
                summary = "Could not set alarm: ${e.message}"
            )
        }
    }

    fun openClock(): CapabilityResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.SUCCESS,
                summary = "Clock interface opened."
            )
        } catch (e: Exception) {
            CapabilityResult(
                status = CapabilityExecutionStatus.FAILED,
                summary = "Could not open clock: ${e.message}"
            )
        }
    }

    // ==========================================
    // 6. CALENDAR CAPABILITY
    // ==========================================
    fun createCalendarEvent(title: String, beginTimeMs: Long, endTimeMs: Long): CapabilityResult {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(
                status = CapabilityExecutionStatus.REQUIRES_USER_ACTION,
                summary = "Calendar event draft created: \"$title\". Review and save in Calendar."
            )
        } catch (e: Exception) {
            CapabilityResult(
                status = CapabilityExecutionStatus.FAILED,
                summary = "Could not create calendar event: ${e.message}"
            )
        }
    }

    // ==========================================
    // 7. CONTROLLED APP LAUNCHER
    // ==========================================
    fun launchApp(appIdentifier: String): CapabilityResult {
        val app = appIdentifier.trim().lowercase()
        return try {
            when (app) {
                "youtube" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Launching YouTube.")
                }
                "maps" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Current+Location")).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Launching Google Maps.")
                }
                "chrome", "browser" -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Opening web browser.")
                }
                "settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Opening Android Settings.")
                }
                else -> {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(appIdentifier)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Launching $appIdentifier.")
                    } else {
                        CapabilityResult(CapabilityExecutionStatus.NOT_AVAILABLE, "App '$appIdentifier' is not installed or not discoverable.")
                    }
                }
            }
        } catch (e: Exception) {
            CapabilityResult(CapabilityExecutionStatus.FAILED, "Failed to launch $appIdentifier: ${e.message}")
        }
    }

    // ==========================================
    // 8. SETTINGS SHORTCUTS
    // ==========================================
    fun openAppSettings(): CapabilityResult {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CapabilityResult(CapabilityExecutionStatus.SUCCESS, "Opened Anin application settings.")
        } catch (e: Exception) {
            CapabilityResult(CapabilityExecutionStatus.FAILED, "Could not open settings: ${e.message}")
        }
    }
}

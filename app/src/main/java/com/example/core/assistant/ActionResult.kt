package com.example.core.assistant

sealed class ActionResult {
    data class Success(
        val message: String,
        val details: String = "",
        val extraData: Map<String, Any> = emptyMap()
    ) : ActionResult()

    data class Failed(
        val reason: String,
        val errorCode: String = "ACTION_FAILED"
    ) : ActionResult()

    data class PermissionRequired(
        val permission: String,
        val featureName: String
    ) : ActionResult()

    data class AppNotInstalled(
        val appName: String,
        val fallbackTriggered: Boolean = false,
        val fallbackUrl: String? = null
    ) : ActionResult()

    data class Unsupported(
        val actionName: String,
        val reason: String
    ) : ActionResult()

    data class BlockedSafety(
        val reason: String,
        val isFinancial: Boolean = true
    ) : ActionResult()
}

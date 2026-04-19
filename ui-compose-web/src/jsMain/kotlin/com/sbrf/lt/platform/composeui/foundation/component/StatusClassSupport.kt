package com.sbrf.lt.platform.composeui.foundation.component

fun runStatusCssClass(status: String?): String {
    val normalized = status?.trim()?.lowercase() ?: "pending"
    return when (normalized) {
        "success_with_warnings" -> "status-badge status-success_with_warnings"
        "not_enabled" -> "status-badge status-not_enabled"
        else -> "status-badge status-$normalized"
    }
}

fun eventEntryCssClass(severity: String?): String =
    when (severity?.uppercase()) {
        "SUCCESS" -> "human-log-entry human-log-entry-success"
        "ERROR" -> "human-log-entry human-log-entry-error"
        "WARNING" -> "human-log-entry human-log-entry-warning"
        else -> "human-log-entry"
    }

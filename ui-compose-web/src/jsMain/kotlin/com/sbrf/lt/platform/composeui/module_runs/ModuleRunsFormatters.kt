package com.sbrf.lt.platform.composeui.module_runs

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

fun formatPercent(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return js("new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value)") as String + "%"
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null) {
        return "-"
    }
    val size = bytes.toDouble()
    return when {
        size < 1024 -> "${bytes} B"
        size < 1024 * 1024 -> "${((size / 1024) * 10).toInt() / 10.0} KB"
        else -> "${((size / (1024 * 1024)) * 10).toInt() / 10.0} MB"
    }
}

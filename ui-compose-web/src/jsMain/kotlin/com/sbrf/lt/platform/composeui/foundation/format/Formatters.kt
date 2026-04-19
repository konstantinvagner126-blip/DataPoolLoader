package com.sbrf.lt.platform.composeui.foundation.format

import kotlinx.browser.window

fun formatDateTime(value: String?): String {
    if (value.isNullOrBlank()) {
        return "-"
    }
    return try {
        js("new Date(value).toLocaleString('ru-RU')") as String
    } catch (_: dynamic) {
        value
    }
}

fun formatCompactDateTime(value: String?): String =
    value?.replace("T", " ")?.removeSuffix("Z") ?: "—"

fun formatNumber(value: Number?): String {
    if (value == null) {
        return "-"
    }
    return try {
        js("new Intl.NumberFormat('ru-RU').format(value)") as String
    } catch (_: dynamic) {
        value.toString()
    }
}

fun formatDuration(
    startedAt: String?,
    finishedAt: String?,
    running: Boolean = false,
): String {
    val startMillis = parseEpochMillis(startedAt) ?: return "-"
    val endMillis = parseEpochMillis(finishedAt) ?: if (running) {
        js("Date.now()") as Double
    } else {
        return "-"
    }
    val durationMillis = (endMillis - startMillis).toLong().coerceAtLeast(0L)
    return formatDurationMillis(durationMillis)
}

fun formatDurationMillis(durationMillis: Long?): String {
    if (durationMillis == null) {
        return "-"
    }
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}ч ${minutes}м ${seconds}с"
        minutes > 0L -> "${minutes}м ${seconds}с"
        else -> "${seconds}с"
    }
}

fun formatByteSize(value: Long?): String {
    if (value == null) {
        return "-"
    }
    return when {
        value >= 1024L * 1024L * 1024L -> "${value / (1024L * 1024L * 1024L)} ГБ"
        value >= 1024L * 1024L -> "${value / (1024L * 1024L)} МБ"
        value >= 1024L -> "${value / 1024L} КБ"
        else -> "$value Б"
    }
}

private fun parseEpochMillis(value: String?): Double? {
    if (value.isNullOrBlank()) {
        return null
    }
    return try {
        val result = js("Date.parse(value)") as Double
        if (result.isNaN()) null else result
    } catch (_: dynamic) {
        null
    }
}

fun statusTone(status: String?): String =
    when (status?.uppercase()) {
        "SUCCESS", "COMPLETED" -> "success"
        "FAILED", "ERROR" -> "danger"
        "RUNNING" -> "primary"
        "WARNING", "SKIPPED" -> "warning"
        else -> "secondary"
    }

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

fun statusTone(status: String?): String =
    when (status?.uppercase()) {
        "SUCCESS", "COMPLETED" -> "success"
        "FAILED", "ERROR" -> "danger"
        "RUNNING" -> "primary"
        "WARNING", "SKIPPED" -> "warning"
        else -> "secondary"
    }

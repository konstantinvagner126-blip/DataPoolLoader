package com.sbrf.lt.platform.composeui.module_runs

import kotlin.math.abs

fun formatPercentValue(value: Double?): String {
    if (value == null) {
        return "-"
    }
    val rounded = if (abs(value - value.toLong().toDouble()) < 0.005) {
        value.toLong().toString()
    } else {
        (kotlin.math.round(value * 100.0) / 100.0).toString().replace('.', ',')
    }
    return "$rounded%"
}

fun formatFileSizeValue(bytes: Long?): String {
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

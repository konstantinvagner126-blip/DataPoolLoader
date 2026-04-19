package com.sbrf.lt.platform.ui.server

internal fun UiServerContext.includeHiddenQueryParam(rawValue: String?): Boolean =
    rawValue == "1" || rawValue.equals("true", ignoreCase = true)

internal fun UiServerContext.parseLimit(rawValue: String?, defaultValue: Int = 20): Int =
    rawValue?.toIntOrNull()?.coerceIn(1, 200) ?: defaultValue

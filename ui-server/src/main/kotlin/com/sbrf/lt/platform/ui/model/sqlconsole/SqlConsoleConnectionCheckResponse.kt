package com.sbrf.lt.platform.ui.model

/**
 * Ответ UI API с результатами проверки подключений SQL-консоли.
 */
data class SqlConsoleConnectionCheckResponse(
    val configured: Boolean,
    val sourceResults: List<SqlConsoleSourceConnectionStatusResponse>,
)

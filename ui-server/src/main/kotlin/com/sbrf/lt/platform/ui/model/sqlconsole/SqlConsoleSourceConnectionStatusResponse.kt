package com.sbrf.lt.platform.ui.model

/**
 * Результат проверки подключения к одному source в SQL-консоли.
 */
data class SqlConsoleSourceConnectionStatusResponse(
    val sourceName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

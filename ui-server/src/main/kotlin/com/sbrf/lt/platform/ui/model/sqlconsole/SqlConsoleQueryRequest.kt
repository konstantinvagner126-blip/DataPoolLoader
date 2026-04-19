package com.sbrf.lt.platform.ui.model

/**
 * Команда выполнения SQL-запроса в SQL-консоли.
 */
data class SqlConsoleQueryRequest(
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
)

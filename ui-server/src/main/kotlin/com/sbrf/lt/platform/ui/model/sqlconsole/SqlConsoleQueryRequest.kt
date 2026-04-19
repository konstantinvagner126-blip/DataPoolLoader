package com.sbrf.lt.platform.ui.model

/**
 * Команда выполнения SQL-запроса в SQL-консоли.
 */
data class SqlConsoleQueryRequest(
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
    val transactionMode: String = "AUTO_COMMIT",
)

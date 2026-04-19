package com.sbrf.lt.platform.ui.model

/**
 * Команда сохранения локального состояния SQL-консоли.
 */
data class SqlConsoleStateUpdateRequest(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
)

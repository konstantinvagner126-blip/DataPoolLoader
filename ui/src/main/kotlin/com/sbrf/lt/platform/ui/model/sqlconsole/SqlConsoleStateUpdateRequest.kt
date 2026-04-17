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
)

package com.sbrf.lt.platform.ui.model

/**
 * Команда поиска объектов БД в SQL-консоли.
 */
data class SqlConsoleObjectSearchRequest(
    val query: String,
    val selectedSourceNames: List<String> = emptyList(),
    val maxObjectsPerSource: Int = 30,
)

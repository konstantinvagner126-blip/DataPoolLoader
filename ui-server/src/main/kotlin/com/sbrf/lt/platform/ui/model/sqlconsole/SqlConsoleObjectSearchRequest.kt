package com.sbrf.lt.platform.ui.model

/**
 * Команда поиска объектов БД в SQL-консоли.
 */
data class SqlConsoleObjectSearchRequest(
    val query: String,
    val selectedSourceNames: List<String> = emptyList(),
    val maxObjectsPerSource: Int = 30,
)

data class SqlConsoleObjectInspectorRequest(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
)

data class SqlConsoleObjectColumnsRequest(
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val selectedSourceNames: List<String> = emptyList(),
)

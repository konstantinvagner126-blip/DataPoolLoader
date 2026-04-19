package com.sbrf.lt.platform.ui.model

/**
 * Результат поискового просмотра объектов БД по источникам SQL-консоли.
 */
data class SqlConsoleObjectSearchResponse(
    val query: String,
    val maxObjectsPerSource: Int,
    val sourceResults: List<SqlConsoleObjectSourceSearchResponse>,
)

data class SqlConsoleObjectSourceSearchResponse(
    val sourceName: String,
    val status: String,
    val objects: List<SqlConsoleDatabaseObjectResponse> = emptyList(),
    val truncated: Boolean = false,
    val errorMessage: String? = null,
)

data class SqlConsoleDatabaseObjectResponse(
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val tableName: String? = null,
    val columns: List<SqlConsoleDatabaseObjectColumnResponse> = emptyList(),
    val indexNames: List<String> = emptyList(),
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectColumnResponse(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

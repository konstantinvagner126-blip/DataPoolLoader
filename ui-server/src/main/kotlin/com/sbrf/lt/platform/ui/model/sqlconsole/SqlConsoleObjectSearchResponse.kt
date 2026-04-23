package com.sbrf.lt.platform.ui.model

/**
 * Результат поискового просмотра объектов БД по источникам SQL-консоли.
 */
data class SqlConsoleObjectSearchResponse(
    val query: String,
    val maxObjectsPerSource: Int,
    val sourceResults: List<SqlConsoleObjectSourceSearchResponse>,
)

data class SqlConsoleObjectInspectorResponse(
    val sourceName: String,
    val dbObject: SqlConsoleDatabaseObjectResponse,
    val definition: String? = null,
    val columns: List<SqlConsoleDatabaseObjectColumnResponse> = emptyList(),
    val indexes: List<SqlConsoleDatabaseObjectIndexResponse> = emptyList(),
    val constraints: List<SqlConsoleDatabaseObjectConstraintResponse> = emptyList(),
    val relatedTriggers: List<SqlConsoleDatabaseObjectTriggerResponse> = emptyList(),
    val trigger: SqlConsoleDatabaseObjectTriggerResponse? = null,
    val sequence: SqlConsoleDatabaseObjectSequenceResponse? = null,
    val schema: SqlConsoleDatabaseObjectSchemaResponse? = null,
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
)

data class SqlConsoleDatabaseObjectColumnResponse(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

data class SqlConsoleDatabaseObjectIndexResponse(
    val name: String,
    val tableName: String? = null,
    val columns: List<String> = emptyList(),
    val unique: Boolean? = null,
    val primary: Boolean? = null,
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectConstraintResponse(
    val name: String,
    val type: String,
    val columns: List<String> = emptyList(),
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectTriggerResponse(
    val name: String,
    val targetObjectName: String? = null,
    val timing: String? = null,
    val events: List<String> = emptyList(),
    val enabled: Boolean? = null,
    val functionName: String? = null,
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectSequenceResponse(
    val incrementBy: String? = null,
    val minimumValue: String? = null,
    val maximumValue: String? = null,
    val startValue: String? = null,
    val cacheSize: String? = null,
    val cycle: Boolean? = null,
    val ownedBy: String? = null,
)

data class SqlConsoleDatabaseObjectSchemaResponse(
    val owner: String? = null,
    val comment: String? = null,
    val privileges: List<String> = emptyList(),
    val objectCounts: List<SqlConsoleDatabaseObjectCountResponse> = emptyList(),
)

data class SqlConsoleDatabaseObjectCountResponse(
    val label: String,
    val count: Int,
)

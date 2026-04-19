package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.Serializable

@Serializable
data class SqlConsoleInfo(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int? = null,
)

@Serializable
data class SqlConsoleStateSnapshot(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
)

@Serializable
data class SqlConsoleStateUpdate(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
)

@Serializable
data class SqlConsoleSettingsUpdate(
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int? = null,
)

@Serializable
data class SqlConsoleQueryStartRequest(
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
)

@Serializable
data class SqlConsoleStartQueryResponse(
    val id: String,
    val status: String,
    val startedAt: String,
    val cancelRequested: Boolean,
    val autoCommitEnabled: Boolean = true,
    val transactionState: String = "NONE",
)

@Serializable
data class SqlConsoleExecutionResponse(
    val id: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val cancelRequested: Boolean,
    val autoCommitEnabled: Boolean = true,
    val transactionState: String = "NONE",
    val transactionShardNames: List<String> = emptyList(),
    val result: SqlConsoleQueryResult? = null,
    val errorMessage: String? = null,
)

@Serializable
data class SqlConsoleQueryResult(
    val sql: String,
    val statementType: String,
    val statementKeyword: String,
    val shardResults: List<SqlConsoleShardResult>,
    val maxRowsPerShard: Int,
    val statementResults: List<SqlConsoleStatementResult> = emptyList(),
)

@Serializable
data class SqlConsoleStatementResult(
    val sql: String,
    val statementType: String,
    val statementKeyword: String,
    val shardResults: List<SqlConsoleShardResult>,
)

@Serializable
data class SqlConsoleShardResult(
    val shardName: String,
    val status: String,
    val rows: List<Map<String, String?>> = emptyList(),
    val rowCount: Int = 0,
    val columns: List<String> = emptyList(),
    val truncated: Boolean = false,
    val affectedRows: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val durationMillis: Long? = null,
)

@Serializable
data class SqlConsoleConnectionCheckResponse(
    val configured: Boolean,
    val sourceResults: List<SqlConsoleSourceConnectionStatus>,
)

@Serializable
data class SqlConsoleObjectSearchRequest(
    val query: String,
    val selectedSourceNames: List<String> = emptyList(),
    val maxObjectsPerSource: Int = 30,
)

@Serializable
data class SqlConsoleObjectSearchResponse(
    val query: String,
    val maxObjectsPerSource: Int,
    val sourceResults: List<SqlConsoleObjectSourceResult>,
)

@Serializable
data class SqlConsoleObjectSourceResult(
    val sourceName: String,
    val status: String,
    val objects: List<SqlConsoleDatabaseObject> = emptyList(),
    val truncated: Boolean = false,
    val errorMessage: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObject(
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val tableName: String? = null,
    val columns: List<SqlConsoleDatabaseObjectColumn> = emptyList(),
    val indexNames: List<String> = emptyList(),
    val definition: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObjectColumn(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

@Serializable
data class SqlConsoleFavoriteObject(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val tableName: String? = null,
)

@Serializable
data class SqlConsoleSourceConnectionStatus(
    val sourceName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class SqlConsolePageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val actionInProgress: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val info: SqlConsoleInfo? = null,
    val connectionCheck: SqlConsoleConnectionCheckResponse? = null,
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
    val maxRowsPerShardDraft: String = "",
    val queryTimeoutSecDraft: String = "",
    val currentExecutionId: String? = null,
    val currentExecution: SqlConsoleExecutionResponse? = null,
)

data class SqlConsoleObjectsPageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val actionInProgress: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val info: SqlConsoleInfo? = null,
    val persistedState: SqlConsoleStateSnapshot? = null,
    val query: String = "",
    val selectedSourceNames: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val searchResponse: SqlConsoleObjectSearchResponse? = null,
)

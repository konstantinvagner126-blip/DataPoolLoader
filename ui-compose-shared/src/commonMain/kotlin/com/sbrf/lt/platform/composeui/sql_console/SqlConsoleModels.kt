package com.sbrf.lt.platform.composeui.sql_console

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
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
)

@Serializable
data class SqlConsoleStateUpdate(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
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
)

@Serializable
data class SqlConsoleStartQueryResponse(
    val id: String,
    val status: String,
    val startedAt: String,
    val cancelRequested: Boolean,
)

@Serializable
data class SqlConsoleExecutionResponse(
    val id: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val cancelRequested: Boolean,
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
)

@Serializable
data class SqlConsoleConnectionCheckResponse(
    val configured: Boolean,
    val sourceResults: List<SqlConsoleSourceConnectionStatus>,
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
    val info: SqlConsoleInfo? = null,
    val connectionCheck: SqlConsoleConnectionCheckResponse? = null,
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val maxRowsPerShardDraft: String = "",
    val queryTimeoutSecDraft: String = "",
    val currentExecutionId: String? = null,
    val currentExecution: SqlConsoleExecutionResponse? = null,
)


package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.Serializable

@Serializable
data class SqlConsoleInfo(
    val configured: Boolean,
    val sourceCatalog: List<SqlConsoleSourceCatalogEntry> = emptyList(),
    val groups: List<SqlConsoleSourceGroup> = emptyList(),
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int? = null,
)

@Serializable
data class SqlConsoleSourceCatalogEntry(
    val name: String,
)

@Serializable
data class SqlConsoleSourceGroup(
    val name: String,
    val sources: List<String> = emptyList(),
    val synthetic: Boolean = false,
)

@Serializable
data class SqlConsoleStateSnapshot(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedGroupNames: List<String>? = null,
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val transactionMode: String = "AUTO_COMMIT",
)

@Serializable
data class SqlConsoleStateUpdate(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedGroupNames: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
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
    val ownerSessionId: String,
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
    val ownerToken: String? = null,
    val ownerLeaseExpiresAt: String? = null,
    val pendingCommitExpiresAt: String? = null,
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
    val ownerToken: String? = null,
    val ownerLeaseExpiresAt: String? = null,
    val pendingCommitExpiresAt: String? = null,
    val result: SqlConsoleQueryResult? = null,
    val errorMessage: String? = null,
)

@Serializable
data class SqlConsoleExecutionOwnerActionRequest(
    val ownerSessionId: String,
    val ownerToken: String,
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
    val connectionState: String? = null,
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
data class SqlConsoleObjectInspectorRequest(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
)

@Serializable
data class SqlConsoleObjectInspectorResponse(
    val sourceName: String,
    val dbObject: SqlConsoleDatabaseObject,
    val definition: String? = null,
    val columns: List<SqlConsoleDatabaseObjectColumn> = emptyList(),
    val indexes: List<SqlConsoleDatabaseObjectIndex> = emptyList(),
    val constraints: List<SqlConsoleDatabaseObjectConstraint> = emptyList(),
    val relatedTriggers: List<SqlConsoleDatabaseObjectTrigger> = emptyList(),
    val trigger: SqlConsoleDatabaseObjectTrigger? = null,
    val sequence: SqlConsoleDatabaseObjectSequence? = null,
    val schema: SqlConsoleDatabaseObjectSchema? = null,
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
)

@Serializable
data class SqlConsoleDatabaseObjectColumn(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

@Serializable
data class SqlConsoleDatabaseObjectIndex(
    val name: String,
    val tableName: String? = null,
    val columns: List<String> = emptyList(),
    val unique: Boolean? = null,
    val primary: Boolean? = null,
    val definition: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObjectConstraint(
    val name: String,
    val type: String,
    val columns: List<String> = emptyList(),
    val definition: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObjectTrigger(
    val name: String,
    val targetObjectName: String? = null,
    val timing: String? = null,
    val events: List<String> = emptyList(),
    val enabled: Boolean? = null,
    val functionName: String? = null,
    val definition: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObjectSequence(
    val incrementBy: String? = null,
    val minimumValue: String? = null,
    val maximumValue: String? = null,
    val startValue: String? = null,
    val cacheSize: String? = null,
    val cycle: Boolean? = null,
    val ownedBy: String? = null,
)

@Serializable
data class SqlConsoleDatabaseObjectSchema(
    val owner: String? = null,
    val comment: String? = null,
    val privileges: List<String> = emptyList(),
    val objectCounts: List<SqlConsoleDatabaseObjectCount> = emptyList(),
)

@Serializable
data class SqlConsoleDatabaseObjectCount(
    val label: String,
    val count: Int,
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
    val sourceStatuses: List<SqlConsoleSourceConnectionStatus> = emptyList(),
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val selectedGroupNames: List<String> = emptyList(),
    val manuallyIncludedSourceNames: List<String> = emptyList(),
    val manuallyExcludedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
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
    val selectedGroupNames: List<String> = emptyList(),
    val manuallyIncludedSourceNames: List<String> = emptyList(),
    val manuallyExcludedSourceNames: List<String> = emptyList(),
    val favoriteObjects: List<SqlConsoleFavoriteObject> = emptyList(),
    val searchResponse: SqlConsoleObjectSearchResponse? = null,
    val inspectorLoading: Boolean = false,
    val inspectorErrorMessage: String? = null,
    val inspectorResponse: SqlConsoleObjectInspectorResponse? = null,
)

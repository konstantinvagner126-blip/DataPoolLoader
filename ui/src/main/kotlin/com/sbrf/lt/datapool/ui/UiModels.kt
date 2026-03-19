package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import java.nio.file.Path
import java.time.Instant

data class ModuleDescriptor(
    val id: String,
    val title: String,
    val configFile: Path,
    val resourcesDir: Path,
)

data class ModuleFileContent(
    val label: String,
    val path: String,
    val content: String,
    val exists: Boolean,
)

data class ModuleDetailsResponse(
    val id: String,
    val title: String,
    val configPath: String,
    val configText: String,
    val sqlFiles: List<ModuleFileContent>,
    val requiresCredentials: Boolean,
    val credentialsStatus: CredentialsStatusResponse,
)

data class SaveModuleRequest(
    val configText: String,
    val sqlFiles: Map<String, String>,
)

data class StartRunRequest(
    val moduleId: String,
    val configText: String,
    val sqlFiles: Map<String, String>,
)

data class UiRunSnapshot(
    val id: String,
    val moduleId: String,
    val moduleTitle: String,
    val status: ExecutionStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val outputDir: String? = null,
    val mergedRowCount: Long = 0,
    val summaryJson: String? = null,
    val errorMessage: String? = null,
    val sourceProgress: Map<String, Long> = emptyMap(),
    val events: List<ExecutionEvent> = emptyList(),
)

data class UiStateResponse(
    val credentialsStatus: CredentialsStatusResponse,
    val activeRun: UiRunSnapshot? = null,
    val history: List<UiRunSnapshot> = emptyList(),
)

data class SaveResultResponse(
    val message: String,
)

data class CredentialsStatusResponse(
    val mode: String,
    val displayName: String,
    val fileAvailable: Boolean,
    val uploaded: Boolean,
)

data class SqlConsoleInfoResponse(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

data class SqlConsoleQueryRequest(
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
)

data class SqlConsoleStartQueryResponse(
    val id: String,
    val status: String,
    val startedAt: Instant,
    val cancelRequested: Boolean,
)

data class SqlConsoleExecutionResponse(
    val id: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val cancelRequested: Boolean,
    val result: SqlConsoleQueryResponse? = null,
    val errorMessage: String? = null,
)

data class SqlConsoleShardResultResponse(
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

data class SqlConsoleQueryResponse(
    val sql: String,
    val statementType: String,
    val statementKeyword: String,
    val shardResults: List<SqlConsoleShardResultResponse>,
    val maxRowsPerShard: Int,
)

fun SqlConsoleInfo.toResponse(): SqlConsoleInfoResponse = SqlConsoleInfoResponse(
    configured = configured,
    sourceNames = sourceNames,
    maxRowsPerShard = maxRowsPerShard,
    queryTimeoutSec = queryTimeoutSec,
)

fun SqlConsoleQueryResult.toResponse(): SqlConsoleQueryResponse = SqlConsoleQueryResponse(
    sql = sql,
    statementType = statementType.name,
    statementKeyword = statementKeyword,
    shardResults = shardResults.map { it.toResponse() },
    maxRowsPerShard = maxRowsPerShard,
)

private fun RawShardExecutionResult.toResponse(): SqlConsoleShardResultResponse = SqlConsoleShardResultResponse(
    shardName = shardName,
    status = status,
    rows = rows,
    rowCount = rows.size,
    columns = columns,
    truncated = truncated,
    affectedRows = affectedRows,
    message = message,
    errorMessage = errorMessage,
)

fun SqlConsoleExecutionSnapshot.toStartResponse(): SqlConsoleStartQueryResponse = SqlConsoleStartQueryResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    cancelRequested = cancelRequested,
)

fun SqlConsoleExecutionSnapshot.toResponse(): SqlConsoleExecutionResponse = SqlConsoleExecutionResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    finishedAt = finishedAt,
    cancelRequested = cancelRequested,
    result = result?.toResponse(),
    errorMessage = errorMessage,
)

package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionSnapshot
import java.nio.file.Path
import java.time.Instant

data class ModuleDescriptor(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
    val configFile: Path,
    val resourcesDir: Path,
)

data class ModuleValidationIssueResponse(
    val severity: String,
    val message: String,
)

data class ModuleCatalogItemResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
)

data class AppsRootStatusResponse(
    val mode: String,
    val configuredPath: String? = null,
    val message: String,
)

data class ModulesCatalogResponse(
    val appsRootStatus: AppsRootStatusResponse,
    val modules: List<ModuleCatalogItemResponse>,
)

data class DatabaseModulesCatalogResponse(
    val runtimeContext: UiRuntimeContext,
    val modules: List<ModuleCatalogItemResponse>,
)

data class DatabaseModuleDetailsResponse(
    val runtimeContext: UiRuntimeContext,
    val module: ModuleDetailsResponse,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String? = null,
    val workingCopyStatus: String? = null,
    val baseRevisionId: String? = null,
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
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
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

data class ConfigFormParseRequest(
    val configText: String,
)

data class ConfigFormSourceState(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val sql: String?,
    val sqlFile: String?,
)

data class ConfigFormQuotaState(
    val source: String,
    val percent: Double?,
)

data class ConfigFormStateResponse(
    val outputDir: String,
    val fileFormat: String,
    val mergeMode: String,
    val errorMode: String,
    val parallelism: Int,
    val fetchSize: Int,
    val queryTimeoutSec: Int?,
    val progressLogEveryRows: Long,
    val maxMergedRows: Long?,
    val deleteOutputFilesAfterCompletion: Boolean,
    val commonSql: String,
    val commonSqlFile: String?,
    val sources: List<ConfigFormSourceState>,
    val quotas: List<ConfigFormQuotaState>,
    val targetEnabled: Boolean,
    val targetJdbcUrl: String,
    val targetUsername: String,
    val targetPassword: String,
    val targetTable: String,
    val targetTruncateBeforeLoad: Boolean,
)

data class ConfigFormUpdateRequest(
    val configText: String,
    val formState: ConfigFormStateResponse,
)

data class ConfigFormUpdateResponse(
    val configText: String,
    val formState: ConfigFormStateResponse,
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
    val events: List<Map<String, Any?>> = emptyList(),
)

data class UiStateResponse(
    val credentialsStatus: CredentialsStatusResponse,
    val uiSettings: UiSettingsResponse = UiSettingsResponse(),
    val activeRun: UiRunSnapshot? = null,
    val history: List<UiRunSnapshot> = emptyList(),
)

data class UiSettingsResponse(
    val showTechnicalDiagnostics: Boolean = true,
    val showRawSummaryJson: Boolean = false,
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

data class SqlConsoleSourceConnectionStatusResponse(
    val sourceName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class SqlConsoleConnectionCheckResponse(
    val configured: Boolean,
    val sourceResults: List<SqlConsoleSourceConnectionStatusResponse>,
)

data class SqlConsoleStateResponse(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
)

data class SqlConsoleStateUpdateRequest(
    val draftSql: String,
    val recentQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
)

data class SqlConsoleSettingsUpdateRequest(
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int? = null,
)

data class SqlConsoleQueryRequest(
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
)

data class SqlConsoleExportRequest(
    val result: SqlConsoleQueryResponse,
    val shardName: String? = null,
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

fun SqlConsoleConnectionCheckResult.toResponse(configured: Boolean): SqlConsoleConnectionCheckResponse = SqlConsoleConnectionCheckResponse(
    configured = configured,
    sourceResults = sourceResults.map { it.toResponse() },
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

private fun RawShardConnectionCheckResult.toResponse(): SqlConsoleSourceConnectionStatusResponse = SqlConsoleSourceConnectionStatusResponse(
    sourceName = shardName,
    status = status,
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

fun ModuleDescriptor.toCatalogItemResponse(): ModuleCatalogItemResponse = ModuleCatalogItemResponse(
    id = id,
    title = title,
    description = description,
    tags = tags,
    validationStatus = validationStatus,
    validationIssues = validationIssues,
)

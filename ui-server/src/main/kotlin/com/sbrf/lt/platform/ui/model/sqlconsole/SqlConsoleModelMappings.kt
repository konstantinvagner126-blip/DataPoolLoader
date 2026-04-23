package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObject
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectConstraint
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumn
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumnLookupResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumnSourceResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectCount
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectIndex
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectInspector
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSearchResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSchema
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSequence
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSourceResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectTrigger
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceCatalogEntry
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroup
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementResult
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionSnapshot
import com.sbrf.lt.platform.ui.sqlconsole.PersistedSqlConsoleExecutionHistoryEntry
import com.sbrf.lt.platform.ui.sqlconsole.PersistedSqlConsoleExecutionHistoryState

/**
 * Преобразования внутренних моделей SQL-консоли в UI DTO.
 */
fun SqlConsoleInfo.toResponse(): SqlConsoleInfoResponse = SqlConsoleInfoResponse(
    configured = configured,
    sourceCatalog = sourceCatalog.map { it.toResponse() },
    groups = groups.map { it.toResponse() },
    maxRowsPerShard = maxRowsPerShard,
    queryTimeoutSec = queryTimeoutSec,
)

private fun SqlConsoleSourceCatalogEntry.toResponse(): SqlConsoleSourceCatalogEntryResponse = SqlConsoleSourceCatalogEntryResponse(
    name = name,
)

private fun SqlConsoleSourceGroup.toResponse(): SqlConsoleSourceGroupResponse = SqlConsoleSourceGroupResponse(
    name = name,
    sources = sources,
    synthetic = synthetic,
)

/**
 * Преобразует результат проверки подключений SQL-консоли в UI-ответ.
 */
fun SqlConsoleConnectionCheckResult.toResponse(configured: Boolean): SqlConsoleConnectionCheckResponse = SqlConsoleConnectionCheckResponse(
    configured = configured,
    sourceResults = sourceResults.map { it.toResponse() },
)

fun SqlConsoleDatabaseObjectSearchResult.toResponse(): SqlConsoleObjectSearchResponse = SqlConsoleObjectSearchResponse(
    query = query,
    maxObjectsPerSource = maxObjectsPerSource,
    sourceResults = sourceResults.map { it.toResponse() },
)

fun SqlConsoleDatabaseObjectInspector.toResponse(sourceName: String): SqlConsoleObjectInspectorResponse = SqlConsoleObjectInspectorResponse(
    sourceName = sourceName,
    dbObject = SqlConsoleDatabaseObject(
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
        tableName = tableName,
    ).toResponse(),
    definition = definition,
    columns = columns.map { it.toResponse() },
    indexes = indexes.map { it.toResponse() },
    constraints = constraints.map { it.toResponse() },
    relatedTriggers = relatedTriggers.map { it.toResponse() },
    trigger = trigger?.toResponse(),
    sequence = sequence?.toResponse(),
    schema = schema?.toResponse(),
)

fun SqlConsoleDatabaseObjectColumnLookupResult.toResponse(): SqlConsoleObjectColumnsResponse = SqlConsoleObjectColumnsResponse(
    schemaName = schemaName,
    objectName = objectName,
    objectType = objectType.name,
    sourceResults = sourceResults.map { it.toResponse() },
)

/**
 * Преобразует синхронный результат SQL-запроса в UI DTO.
 */
fun SqlConsoleQueryResult.toResponse(): SqlConsoleQueryResponse = SqlConsoleQueryResponse(
    sql = sql,
    statementType = statementType.name,
    statementKeyword = statementKeyword,
    shardResults = shardResults.map { it.toResponse() },
    maxRowsPerShard = maxRowsPerShard,
    statementResults = statementResults.map { it.toResponse() },
)

/**
 * Преобразует snapshot асинхронного запроса в краткий стартовый ответ.
 */
fun SqlConsoleExecutionSnapshot.toStartResponse(
    includeOwnerToken: Boolean = false,
    ownerToken: String? = null,
): SqlConsoleStartQueryResponse = SqlConsoleStartQueryResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    cancelRequested = cancelRequested,
    autoCommitEnabled = autoCommitEnabled,
    transactionState = transactionState.name,
    ownerToken = if (includeOwnerToken) ownerToken ?: this.ownerToken else null,
    ownerLeaseExpiresAt = ownerLeaseExpiresAt,
    pendingCommitExpiresAt = pendingCommitExpiresAt,
)

/**
 * Преобразует snapshot асинхронного запроса в полное состояние для polling.
 */
fun SqlConsoleExecutionSnapshot.toResponse(
    includeOwnerToken: Boolean = false,
    ownerToken: String? = null,
): SqlConsoleExecutionResponse = SqlConsoleExecutionResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    finishedAt = finishedAt,
    cancelRequested = cancelRequested,
    autoCommitEnabled = autoCommitEnabled,
    transactionState = transactionState.name,
    transactionShardNames = transactionShardNames,
    ownerToken = if (includeOwnerToken) ownerToken ?: this.ownerToken else null,
    ownerLeaseExpiresAt = ownerLeaseExpiresAt,
    pendingCommitExpiresAt = pendingCommitExpiresAt,
    result = result?.toResponse(),
    errorMessage = errorMessage,
)

internal fun PersistedSqlConsoleExecutionHistoryState.toResponse(): SqlConsoleExecutionHistoryResponse = SqlConsoleExecutionHistoryResponse(
    entries = entries.map { it.toResponse() },
)

private fun PersistedSqlConsoleExecutionHistoryEntry.toResponse(): SqlConsoleExecutionHistoryEntryResponse =
    SqlConsoleExecutionHistoryEntryResponse(
        executionId = executionId,
        sql = sql,
        selectedSourceNames = selectedSourceNames,
        autoCommitEnabled = autoCommitEnabled,
        status = status,
        transactionState = transactionState,
        startedAt = startedAt.toString(),
        finishedAt = finishedAt?.toString(),
        durationMillis = durationMillis,
        errorMessage = errorMessage,
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
    connectionState = connectionState?.name,
    startedAt = startedAt,
    finishedAt = finishedAt,
    durationMillis = durationMillis,
)

private fun SqlConsoleStatementResult.toResponse(): SqlConsoleStatementResultResponse = SqlConsoleStatementResultResponse(
    sql = sql,
    statementType = statementType.name,
    statementKeyword = statementKeyword,
    shardResults = shardResults.map { it.toResponse() },
)

private fun RawShardConnectionCheckResult.toResponse(): SqlConsoleSourceConnectionStatusResponse = SqlConsoleSourceConnectionStatusResponse(
    sourceName = shardName,
    status = status,
    message = message,
    errorMessage = errorMessage,
)

private fun SqlConsoleDatabaseObjectSourceResult.toResponse(): SqlConsoleObjectSourceSearchResponse = SqlConsoleObjectSourceSearchResponse(
    sourceName = sourceName,
    status = status,
    objects = objects.map { it.toResponse() },
    truncated = truncated,
    errorMessage = errorMessage,
)

private fun SqlConsoleDatabaseObjectColumnSourceResult.toResponse(): SqlConsoleObjectColumnSourceResponse = SqlConsoleObjectColumnSourceResponse(
    sourceName = sourceName,
    status = status,
    columns = columns.map { it.toResponse() },
    errorMessage = errorMessage,
)

private fun SqlConsoleDatabaseObject.toResponse(): SqlConsoleDatabaseObjectResponse = SqlConsoleDatabaseObjectResponse(
    schemaName = schemaName,
    objectName = objectName,
    objectType = objectType.name,
    tableName = tableName,
)

private fun SqlConsoleDatabaseObjectColumn.toResponse(): SqlConsoleDatabaseObjectColumnResponse =
    SqlConsoleDatabaseObjectColumnResponse(
        name = name,
        type = type,
        nullable = nullable,
    )

private fun SqlConsoleDatabaseObjectIndex.toResponse(): SqlConsoleDatabaseObjectIndexResponse =
    SqlConsoleDatabaseObjectIndexResponse(
        name = name,
        tableName = tableName,
        columns = columns,
        unique = unique,
        primary = primary,
        definition = definition,
    )

private fun SqlConsoleDatabaseObjectConstraint.toResponse(): SqlConsoleDatabaseObjectConstraintResponse =
    SqlConsoleDatabaseObjectConstraintResponse(
        name = name,
        type = type,
        columns = columns,
        definition = definition,
    )

private fun SqlConsoleDatabaseObjectTrigger.toResponse(): SqlConsoleDatabaseObjectTriggerResponse =
    SqlConsoleDatabaseObjectTriggerResponse(
        name = name,
        targetObjectName = targetObjectName,
        timing = timing,
        events = events,
        enabled = enabled,
        functionName = functionName,
        definition = definition,
    )

private fun SqlConsoleDatabaseObjectSequence.toResponse(): SqlConsoleDatabaseObjectSequenceResponse =
    SqlConsoleDatabaseObjectSequenceResponse(
        incrementBy = incrementBy,
        minimumValue = minimumValue,
        maximumValue = maximumValue,
        startValue = startValue,
        cacheSize = cacheSize,
        cycle = cycle,
        ownedBy = ownedBy,
    )

private fun SqlConsoleDatabaseObjectSchema.toResponse(): SqlConsoleDatabaseObjectSchemaResponse =
    SqlConsoleDatabaseObjectSchemaResponse(
        owner = owner,
        comment = comment,
        privileges = privileges,
        objectCounts = objectCounts.map { it.toResponse() },
    )

private fun SqlConsoleDatabaseObjectCount.toResponse(): SqlConsoleDatabaseObjectCountResponse =
    SqlConsoleDatabaseObjectCountResponse(
        label = label,
        count = count,
    )

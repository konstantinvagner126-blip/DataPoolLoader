package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementResult
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionSnapshot

/**
 * Преобразования внутренних моделей SQL-консоли в UI DTO.
 */
fun SqlConsoleInfo.toResponse(): SqlConsoleInfoResponse = SqlConsoleInfoResponse(
    configured = configured,
    sourceNames = sourceNames,
    maxRowsPerShard = maxRowsPerShard,
    queryTimeoutSec = queryTimeoutSec,
)

/**
 * Преобразует результат проверки подключений SQL-консоли в UI-ответ.
 */
fun SqlConsoleConnectionCheckResult.toResponse(configured: Boolean): SqlConsoleConnectionCheckResponse = SqlConsoleConnectionCheckResponse(
    configured = configured,
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
fun SqlConsoleExecutionSnapshot.toStartResponse(): SqlConsoleStartQueryResponse = SqlConsoleStartQueryResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    cancelRequested = cancelRequested,
)

/**
 * Преобразует snapshot асинхронного запроса в полное состояние для polling.
 */
fun SqlConsoleExecutionSnapshot.toResponse(): SqlConsoleExecutionResponse = SqlConsoleExecutionResponse(
    id = id,
    status = status.name,
    startedAt = startedAt,
    finishedAt = finishedAt,
    cancelRequested = cancelRequested,
    result = result?.toResponse(),
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

package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.platform.ui.model.SqlConsoleExecutionOwnerActionRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleExecutionResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleStartQueryResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse

internal fun UiServerContext.startSqlConsoleQuery(
    request: SqlConsoleQueryRequest,
): SqlConsoleStartQueryResponse {
    val executionPaths = createSqlConsoleExecutionPaths("datapool-ui-sql-console-")
    return try {
        sqlConsoleQueryManager.startQuery(
            sql = request.sql,
            credentialsPath = executionPaths.credentialsPath,
            selectedSourceNames = request.selectedSourceNames,
            ownerSessionId = request.ownerSessionId,
            executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
            transactionMode = request.toTransactionMode(),
            cleanupDir = executionPaths.cleanupDir,
        ).toStartResponse(includeOwnerToken = true)
    } catch (ex: Exception) {
        executionPaths.cleanupDir.toFile().deleteRecursively()
        throw ex
    }
}

internal fun UiServerContext.loadSqlConsoleExecution(executionId: String): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.snapshot(executionId).toResponse()

internal fun UiServerContext.heartbeatSqlConsoleExecution(
    executionId: String,
    request: SqlConsoleExecutionOwnerActionRequest,
): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.heartbeat(
        executionId = executionId,
        ownerSessionId = request.ownerSessionId,
        ownerToken = request.ownerToken,
    ).toResponse(includeOwnerToken = true, ownerToken = request.ownerToken)

internal fun UiServerContext.cancelSqlConsoleExecution(
    executionId: String,
    request: SqlConsoleExecutionOwnerActionRequest,
): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.cancel(
        executionId = executionId,
        ownerSessionId = request.ownerSessionId,
        ownerToken = request.ownerToken,
    ).toResponse(includeOwnerToken = true, ownerToken = request.ownerToken)

internal fun UiServerContext.commitSqlConsoleExecution(
    executionId: String,
    request: SqlConsoleExecutionOwnerActionRequest,
): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.commit(
        executionId = executionId,
        ownerSessionId = request.ownerSessionId,
        ownerToken = request.ownerToken,
    ).toResponse()

internal fun UiServerContext.rollbackSqlConsoleExecution(
    executionId: String,
    request: SqlConsoleExecutionOwnerActionRequest,
): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.rollback(
        executionId = executionId,
        ownerSessionId = request.ownerSessionId,
        ownerToken = request.ownerToken,
    ).toResponse()

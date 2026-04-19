package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.platform.ui.model.SqlConsoleExecutionResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStartQueryResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse
import io.ktor.server.application.ApplicationCall
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

internal data class SqlConsoleExecutionPaths(
    val cleanupDir: Path,
    val credentialsPath: Path?,
)

internal fun SqlConsoleQueryRequest.toTransactionMode(): SqlConsoleTransactionMode =
    runCatching { SqlConsoleTransactionMode.valueOf(transactionMode.uppercase()) }
        .getOrDefault(SqlConsoleTransactionMode.AUTO_COMMIT)

internal fun ApplicationCall.requireSqlConsoleExecutionId(): String =
    requireRouteParam("id")

internal inline fun <T> UiServerContext.withSqlConsoleCredentialsPath(
    prefix: String,
    block: (Path?) -> T,
): T {
    val tempDir = createTempDirectory(prefix)
    try {
        val credentialsPath = filesRunService.materializeCredentialsFile(tempDir)
        return block(credentialsPath)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}

internal fun UiServerContext.createSqlConsoleExecutionPaths(prefix: String): SqlConsoleExecutionPaths {
    val tempDir = createTempDirectory(prefix)
    return try {
        SqlConsoleExecutionPaths(
            cleanupDir = tempDir,
            credentialsPath = filesRunService.materializeCredentialsFile(tempDir),
        )
    } catch (ex: Exception) {
        tempDir.toFile().deleteRecursively()
        throw ex
    }
}

internal fun UiServerContext.executeSqlConsoleQuery(
    request: SqlConsoleQueryRequest,
): SqlConsoleQueryResponse =
    withSqlConsoleCredentialsPath("datapool-ui-sql-console-") { credentialsPath ->
        sqlConsoleService.executeQuery(
            rawSql = request.sql,
            credentialsPath = credentialsPath,
            selectedSourceNames = request.selectedSourceNames,
            executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
            transactionMode = request.toTransactionMode(),
        ).toResponse()
    }

internal fun UiServerContext.startSqlConsoleQuery(
    request: SqlConsoleQueryRequest,
): SqlConsoleStartQueryResponse {
    val executionPaths = createSqlConsoleExecutionPaths("datapool-ui-sql-console-")
    return try {
        sqlConsoleQueryManager.startQuery(
            sql = request.sql,
            credentialsPath = executionPaths.credentialsPath,
            selectedSourceNames = request.selectedSourceNames,
            executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
            transactionMode = request.toTransactionMode(),
            cleanupDir = executionPaths.cleanupDir,
        ).toStartResponse()
    } catch (ex: Exception) {
        executionPaths.cleanupDir.toFile().deleteRecursively()
        throw ex
    }
}

internal fun UiServerContext.loadSqlConsoleExecution(executionId: String): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.snapshot(executionId).toResponse()

internal fun UiServerContext.cancelSqlConsoleExecution(executionId: String): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.cancel(executionId).toResponse()

internal fun UiServerContext.commitSqlConsoleExecution(executionId: String): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.commit(executionId).toResponse()

internal fun UiServerContext.rollbackSqlConsoleExecution(executionId: String): SqlConsoleExecutionResponse =
    sqlConsoleQueryManager.rollback(executionId).toResponse()

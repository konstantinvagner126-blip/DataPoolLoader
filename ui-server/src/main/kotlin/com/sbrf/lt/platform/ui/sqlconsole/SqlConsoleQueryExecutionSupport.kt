package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import java.nio.file.Path
import java.time.Instant

internal class SqlConsoleQueryExecutionSupport(
    private val sqlConsoleService: SqlConsoleOperations,
) {
    fun execute(
        execution: ActiveExecution,
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        cleanupDir: Path?,
    ): SqlConsoleExecutionSnapshot {
        return try {
            execution.snapshot.copy(
                status = SqlConsoleExecutionStatus.SUCCESS,
                finishedAt = Instant.now(),
                result = sqlConsoleService.executeQuery(
                    rawSql = sql,
                    credentialsPath = credentialsPath,
                    selectedSourceNames = selectedSourceNames,
                    executionControl = execution.control,
                ),
            )
        } catch (ex: SqlConsoleExecutionCancelledException) {
            execution.snapshot.copy(
                status = SqlConsoleExecutionStatus.CANCELLED,
                finishedAt = Instant.now(),
                cancelRequested = true,
                errorMessage = ex.message ?: "Запрос отменен пользователем.",
            )
        } catch (ex: Exception) {
            val cancelled = execution.control.isCancelled()
            execution.snapshot.copy(
                status = if (cancelled) SqlConsoleExecutionStatus.CANCELLED else SqlConsoleExecutionStatus.FAILED,
                finishedAt = Instant.now(),
                cancelRequested = cancelled || execution.snapshot.cancelRequested,
                errorMessage = ex.message ?: "Не удалось выполнить запрос.",
            )
        } finally {
            cleanupDir?.toFile()?.deleteRecursively()
        }
    }
}

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import java.nio.file.Path
import java.time.Instant

internal class SqlConsoleQueryExecutionSupport(
    private val sqlConsoleService: SqlConsoleOperations,
    private val transactionalOperations: SqlConsoleTransactionalOperations,
) {
    fun execute(
        execution: ActiveExecution,
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        cleanupDir: Path?,
    ): ActiveExecution {
        return try {
            val executionRun = if (transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT) {
                com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionRun(
                    result = sqlConsoleService.executeQuery(
                        rawSql = sql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = selectedSourceNames,
                        executionPolicy = executionPolicy,
                        transactionMode = transactionMode,
                        executionControl = execution.control,
                    ),
                )
            } else {
                transactionalOperations.executeQueryRun(
                    rawSql = sql,
                    credentialsPath = credentialsPath,
                    selectedSourceNames = selectedSourceNames,
                    autoCommitEnabled = false,
                    executionControl = execution.control,
                )
            }
            execution.copy(
                snapshot = execution.snapshot.copy(
                    status = SqlConsoleExecutionStatus.SUCCESS,
                    finishedAt = Instant.now(),
                    autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
                    transactionState = if (executionRun.pendingTransaction == null) {
                        SqlConsoleExecutionTransactionState.NONE
                    } else {
                        SqlConsoleExecutionTransactionState.PENDING_COMMIT
                    },
                    transactionShardNames = executionRun.pendingTransaction?.shardNames ?: emptyList(),
                    result = executionRun.result,
                ),
                pendingTransaction = executionRun.pendingTransaction,
            )
        } catch (ex: SqlConsoleExecutionCancelledException) {
            execution.copy(
                snapshot = execution.snapshot.copy(
                    status = SqlConsoleExecutionStatus.CANCELLED,
                    finishedAt = Instant.now(),
                    cancelRequested = true,
                    autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
                    errorMessage = ex.message ?: "Запрос отменен пользователем.",
                ),
            )
        } catch (ex: Exception) {
            val cancelled = execution.control.isCancelled()
            execution.copy(
                snapshot = execution.snapshot.copy(
                    status = if (cancelled) SqlConsoleExecutionStatus.CANCELLED else SqlConsoleExecutionStatus.FAILED,
                    finishedAt = Instant.now(),
                    cancelRequested = cancelled || execution.snapshot.cancelRequested,
                    autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
                    errorMessage = ex.message ?: "Не удалось выполнить запрос.",
                ),
            )
        } finally {
            cleanupDir?.toFile()?.deleteRecursively()
        }
    }
}

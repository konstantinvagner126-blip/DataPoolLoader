package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class SqlConsoleExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

class SqlConsoleQueryManager(
    private val sqlConsoleService: SqlConsoleOperations,
    private val transactionalOperations: SqlConsoleTransactionalOperations = sqlConsoleService as? SqlConsoleTransactionalOperations
        ?: error("SQL-консоль не поддерживает транзакционные execution session."),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : SqlConsoleAsyncQueryOperations {
    private val lock = Any()
    private var activeExecution: ActiveExecution? = null
    private val executionSupport = SqlConsoleQueryExecutionSupport(sqlConsoleService, transactionalOperations)

    override fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        cleanupDir: Path?,
    ): SqlConsoleExecutionSnapshot {
        val execution = synchronized(lock) {
            require(activeExecution?.snapshot?.status != SqlConsoleExecutionStatus.RUNNING) {
                "В SQL-консоли уже выполняется запрос. Дождись завершения или отмени его."
            }
            require(activeExecution?.snapshot?.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
                "Есть незавершенная транзакция SQL-консоли. Сначала выполни коммит или роллбек."
            }
            ActiveExecution(
                snapshot = SqlConsoleExecutionSnapshot(
                    id = UUID.randomUUID().toString(),
                    status = SqlConsoleExecutionStatus.RUNNING,
                    startedAt = Instant.now(),
                    autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
                ),
                control = SqlConsoleExecutionControl(),
            ).also { activeExecution = it }
        }

        executor.submit {
            val finalExecution = executionSupport.execute(
                execution = execution,
                sql = sql,
                credentialsPath = credentialsPath,
                selectedSourceNames = selectedSourceNames,
                executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
                transactionMode = transactionMode,
                cleanupDir = cleanupDir,
            )
            synchronized(lock) {
                if (activeExecution?.snapshot?.id == execution.snapshot.id) {
                    activeExecution = finalExecution
                }
            }
        }

        return execution.snapshot
    }

    override fun currentSnapshot(): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        activeExecution?.snapshot
    }

    override fun snapshot(executionId: String): SqlConsoleExecutionSnapshot {
        return synchronized(lock) {
            val execution = activeExecution
            require(execution != null && execution.snapshot.id == executionId) {
                "Запуск SQL-консоли $executionId не найден."
            }
            execution.snapshot
        }
    }

    override fun cancel(executionId: String): SqlConsoleExecutionSnapshot {
        val execution = synchronized(lock) {
            val current = activeExecution
            require(current != null && current.snapshot.id == executionId) {
                "Запуск SQL-консоли $executionId не найден."
            }
            require(current.snapshot.status == SqlConsoleExecutionStatus.RUNNING) {
                "Запрос SQL-консоли уже завершен."
            }
            current.control.cancel()
            val updated = current.copy(snapshot = current.snapshot.copy(cancelRequested = true))
            activeExecution = updated
            updated
        }
        return execution.snapshot
    }

    override fun commit(executionId: String): SqlConsoleExecutionSnapshot {
        return finalizeTransaction(
            executionId = executionId,
            targetState = SqlConsoleExecutionTransactionState.COMMITTED,
        ) { it.commit() }
    }

    override fun rollback(executionId: String): SqlConsoleExecutionSnapshot {
        return finalizeTransaction(
            executionId = executionId,
            targetState = SqlConsoleExecutionTransactionState.ROLLED_BACK,
        ) { it.rollback() }
    }

    private fun finalizeTransaction(
        executionId: String,
        targetState: SqlConsoleExecutionTransactionState,
        action: (com.sbrf.lt.datapool.sqlconsole.SqlConsolePendingTransaction) -> Unit,
    ): SqlConsoleExecutionSnapshot {
        return synchronized(lock) {
            val current = activeExecution
            require(current != null && current.snapshot.id == executionId) {
                "Запуск SQL-консоли $executionId не найден."
            }
            require(current.snapshot.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
                "Для этого запуска нет незавершенной транзакции."
            }
            val pendingTransaction = requireNotNull(current.pendingTransaction)
            action(pendingTransaction)
            val updated = current.copy(
                snapshot = current.snapshot.copy(
                    transactionState = targetState,
                    transactionShardNames = emptyList(),
                ),
                pendingTransaction = null,
            )
            activeExecution = updated
            updated.snapshot
        }
    }
}

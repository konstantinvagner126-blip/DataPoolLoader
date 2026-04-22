package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.time.Instant
import java.util.UUID

internal class SqlConsoleQueryStateSupport {
    private val lock = Any()
    private var activeExecution: ActiveExecution? = null

    fun prepareStart(autoCommitEnabled: Boolean): ActiveExecution = synchronized(lock) {
        if (activeExecution?.snapshot?.status == SqlConsoleExecutionStatus.RUNNING) {
            throw UiStateConflictException("В SQL-консоли уже выполняется запрос. Дождись завершения или отмени его.")
        }
        if (activeExecution?.snapshot?.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
            throw UiStateConflictException("Есть незавершенная транзакция SQL-консоли. Сначала выполни коммит или роллбек.")
        }
        ActiveExecution(
            snapshot = SqlConsoleExecutionSnapshot(
                id = UUID.randomUUID().toString(),
                status = SqlConsoleExecutionStatus.RUNNING,
                startedAt = Instant.now(),
                autoCommitEnabled = autoCommitEnabled,
            ),
            control = SqlConsoleExecutionControl(),
        ).also { activeExecution = it }
    }

    fun storeCompletedExecution(executionId: String, finalExecution: ActiveExecution) = synchronized(lock) {
        if (activeExecution?.snapshot?.id == executionId) {
            activeExecution = finalExecution
        }
    }

    fun currentSnapshot(): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        activeExecution?.snapshot
    }

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot = synchronized(lock) {
        requireExecution(executionId).snapshot
    }

    fun cancel(executionId: String): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val current = requireExecution(executionId)
        if (current.snapshot.status != SqlConsoleExecutionStatus.RUNNING) {
            throw UiStateConflictException("Запрос SQL-консоли уже завершен.")
        }
        current.control.cancel()
        val updated = current.copy(snapshot = current.snapshot.copy(cancelRequested = true))
        activeExecution = updated
        updated.snapshot
    }

    fun updateExecution(
        executionId: String,
        update: (ActiveExecution) -> ActiveExecution,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val updated = update(requireExecution(executionId))
        activeExecution = updated
        updated.snapshot
    }

    private fun requireExecution(executionId: String): ActiveExecution {
        val execution = activeExecution
        if (execution == null || execution.snapshot.id != executionId) {
            throw UiEntityNotFoundException("Запуск SQL-консоли $executionId не найден.")
        }
        return execution
    }
}

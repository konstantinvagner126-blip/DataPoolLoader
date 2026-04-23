package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal class SqlConsoleQueryStateSupport(
    private val ownerLeaseDuration: Duration = Duration.ofSeconds(15),
    private val pendingCommitTtl: Duration = Duration.ofMinutes(2),
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private var activeExecution: ActiveExecution? = null

    fun prepareStart(
        autoCommitEnabled: Boolean,
        ownerSessionId: String,
        now: Instant,
    ): ActiveExecution = synchronized(lock) {
        if (activeExecution?.snapshot?.status == SqlConsoleExecutionStatus.RUNNING) {
            throw UiStateConflictException("В SQL-консоли уже выполняется запрос. Дождись завершения или отмени его.")
        }
        if (activeExecution?.snapshot?.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
            throw UiStateConflictException("Есть незавершенная транзакция SQL-консоли. Сначала выполни коммит или роллбек.")
        }
        val ownerToken = tokenFactory()
        ActiveExecution(
            snapshot = SqlConsoleExecutionSnapshot(
                id = UUID.randomUUID().toString(),
                status = SqlConsoleExecutionStatus.RUNNING,
                startedAt = now,
                autoCommitEnabled = autoCommitEnabled,
                ownerToken = ownerToken,
                ownerLeaseExpiresAt = now.plus(ownerLeaseDuration),
            ),
            control = SqlConsoleExecutionControl(),
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        ).also { activeExecution = it }
    }

    fun storeCompletedExecution(
        executionId: String,
        finalExecution: ActiveExecution,
        now: Instant,
    ) = synchronized(lock) {
        val current = activeExecution
        if (current?.snapshot?.id != executionId) {
            return
        }

        val pendingTransaction = finalExecution.pendingTransaction
        if (pendingTransaction == null) {
            activeExecution = finalExecution.copy(
                snapshot = finalExecution.snapshot.copy(
                    ownerToken = current.ownerToken,
                    ownerLeaseExpiresAt = null,
                    pendingCommitExpiresAt = null,
                ),
                ownerSessionId = current.ownerSessionId,
                ownerToken = current.ownerToken,
                ownerLost = current.ownerLost,
            )
            return
        }

        val ownerLost = current.ownerLost || isLeaseExpired(current.snapshot, now)
        if (ownerLost) {
            pendingTransaction.rollback()
            activeExecution = finalExecution.copy(
                snapshot = finalExecution.snapshot.copy(
                    transactionState = SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS,
                    transactionShardNames = emptyList(),
                    ownerToken = current.ownerToken,
                    ownerLeaseExpiresAt = null,
                    pendingCommitExpiresAt = null,
                    errorMessage = "Транзакция автоматически откатана: владелец execution session потерян.",
                ),
                pendingTransaction = null,
                ownerSessionId = current.ownerSessionId,
                ownerToken = current.ownerToken,
                ownerLost = true,
            )
            return
        }

        activeExecution = finalExecution.copy(
            snapshot = finalExecution.snapshot.copy(
                ownerToken = current.ownerToken,
                ownerLeaseExpiresAt = now.plus(ownerLeaseDuration),
                pendingCommitExpiresAt = now.plus(pendingCommitTtl),
            ),
            ownerSessionId = current.ownerSessionId,
            ownerToken = current.ownerToken,
            ownerLost = false,
        )
    }

    fun currentSnapshot(): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        activeExecution?.snapshot
    }

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot = synchronized(lock) {
        requireExecution(executionId).snapshot
    }

    fun heartbeat(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        now: Instant,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val current = requireOwnedExecution(executionId, ownerSessionId, ownerToken)
        if (current.snapshot.status != SqlConsoleExecutionStatus.RUNNING &&
            current.snapshot.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT
        ) {
            throw UiStateConflictException("Execution session SQL-консоли больше не требует heartbeat.")
        }
        if (current.ownerLost) {
            throw UiStateConflictException("Execution session SQL-консоли уже потеряла владельца.")
        }
        val rotatedOwnerToken = tokenFactory()
        val updated = current.copy(
            snapshot = current.snapshot.copy(
                ownerToken = rotatedOwnerToken,
                ownerLeaseExpiresAt = now.plus(ownerLeaseDuration),
            ),
            ownerToken = rotatedOwnerToken,
        )
        activeExecution = updated
        updated.snapshot
    }

    fun cancel(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val current = requireOwnedExecution(executionId, ownerSessionId, ownerToken)
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
        ownerSessionId: String,
        ownerToken: String,
        update: (ActiveExecution) -> ActiveExecution,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val updated = update(requireOwnedExecution(executionId, ownerSessionId, ownerToken))
        activeExecution = updated
        updated.snapshot
    }

    fun enforceSafetyTimeouts(now: Instant): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        val current = activeExecution ?: return null

        if (current.snapshot.status == SqlConsoleExecutionStatus.RUNNING &&
            !current.ownerLost &&
            isLeaseExpired(current.snapshot, now)
        ) {
            activeExecution = current.copy(ownerLost = true)
            return activeExecution?.snapshot
        }

        val activePending = activeExecution ?: return null
        if (activePending.snapshot.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
            return activePending.snapshot
        }
        if (activePending.pendingTransaction == null) {
            return activePending.snapshot
        }

        val timeoutExpired = activePending.snapshot.pendingCommitExpiresAt?.let { !now.isBefore(it) } == true
        val ownerLeaseExpired = activePending.ownerLost || isLeaseExpired(activePending.snapshot, now)
        if (!timeoutExpired && !ownerLeaseExpired) {
            return activePending.snapshot
        }

        activePending.pendingTransaction.rollback()
        activeExecution = activePending.copy(
            snapshot = activePending.snapshot.copy(
                transactionState = if (timeoutExpired) {
                    SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_TIMEOUT
                } else {
                    SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS
                },
                transactionShardNames = emptyList(),
                ownerToken = activePending.ownerToken,
                ownerLeaseExpiresAt = null,
                pendingCommitExpiresAt = null,
                errorMessage = if (timeoutExpired) {
                    "Транзакция автоматически откатана: истек допустимый TTL ожидания commit."
                } else {
                    "Транзакция автоматически откатана: владелец execution session потерян."
                },
            ),
            pendingTransaction = null,
            ownerLost = ownerLeaseExpired,
        )
        return activeExecution?.snapshot
    }

    private fun requireExecution(executionId: String): ActiveExecution {
        val execution = activeExecution
        if (execution == null || execution.snapshot.id != executionId) {
            throw UiEntityNotFoundException("Запуск SQL-консоли $executionId не найден.")
        }
        return execution
    }

    private fun requireOwnedExecution(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): ActiveExecution {
        val execution = requireExecution(executionId)
        if (execution.ownerSessionId != ownerSessionId || execution.ownerToken != ownerToken) {
            throw UiStateConflictException("Execution session SQL-консоли больше не принадлежит этой вкладке.")
        }
        if (execution.ownerLost) {
            throw UiStateConflictException("Execution session SQL-консоли уже потеряла владельца.")
        }
        return execution
    }

    private fun isLeaseExpired(
        snapshot: SqlConsoleExecutionSnapshot,
        now: Instant,
    ): Boolean = snapshot.ownerLeaseExpiresAt?.let { !now.isBefore(it) } == true
}

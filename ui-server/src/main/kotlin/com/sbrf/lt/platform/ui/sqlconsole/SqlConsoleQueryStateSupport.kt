package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val MANUAL_TRANSACTION_PENDING_CONFLICT_MESSAGE =
    "В другой вкладке SQL-консоли есть незавершенная транзакция. Сначала выполните Commit или Rollback в той вкладке. Пока транзакция не завершена, запуск новой ручной транзакции недоступен."

private const val MANUAL_TRANSACTION_RUNNING_CONFLICT_MESSAGE =
    "В другой вкладке SQL-консоли уже выполняется ручная транзакция. Дождись завершения или отмени ее, прежде чем запускать новую."

internal class SqlConsoleQueryStateSupport(
    private val ownerLeaseDuration: Duration = Duration.ofSeconds(15),
    private val ownerReleaseRecoveryWindow: Duration = Duration.ofSeconds(5),
    private val pendingCommitTtl: Duration = Duration.ofMinutes(2),
    private val completedRetentionLimit: Int = 100,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val executionsById = linkedMapOf<String, ActiveExecution>()

    fun prepareStart(
        autoCommitEnabled: Boolean,
        workspaceId: String?,
        ownerSessionId: String,
        now: Instant,
    ): ActiveExecution = synchronized(lock) {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        if (!autoCommitEnabled) {
            if (executionsById.values.any { it.snapshot.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT }) {
                throw UiStateConflictException(MANUAL_TRANSACTION_PENDING_CONFLICT_MESSAGE)
            }
            if (executionsById.values.any { it.snapshot.status == SqlConsoleExecutionStatus.RUNNING && !it.snapshot.autoCommitEnabled }) {
                throw UiStateConflictException(MANUAL_TRANSACTION_RUNNING_CONFLICT_MESSAGE)
            }
        }
        val ownerToken = tokenFactory()
        val execution = ActiveExecution(
            snapshot = SqlConsoleExecutionSnapshot(
                id = UUID.randomUUID().toString(),
                status = SqlConsoleExecutionStatus.RUNNING,
                startedAt = now,
                autoCommitEnabled = autoCommitEnabled,
                ownerToken = ownerToken,
                ownerLeaseExpiresAt = now.plus(ownerLeaseDuration),
            ),
            control = SqlConsoleExecutionControl(),
            workspaceId = normalizedWorkspaceId,
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        )
        executionsById[execution.snapshot.id] = execution
        pruneCompletedExecutions()
        execution
    }

    fun storeCompletedExecution(
        executionId: String,
        finalExecution: ActiveExecution,
        now: Instant,
    ) = synchronized(lock) {
        val current = executionsById[executionId] ?: return

        val updated = when (val pendingTransaction = finalExecution.pendingTransaction) {
            null -> {
                finalExecution.copy(
                    snapshot = finalExecution.snapshot.copy(
                        ownerToken = current.ownerToken,
                        ownerLeaseExpiresAt = null,
                        pendingCommitExpiresAt = null,
                    ),
                    workspaceId = current.workspaceId,
                    ownerSessionId = current.ownerSessionId,
                    ownerToken = current.ownerToken,
                    ownerLost = current.ownerLost,
                    ownerReleaseDeadline = null,
                )
            }

            else -> {
                val ownerLost = current.ownerLost || isLeaseExpired(current.snapshot, now) || isOwnerReleaseExpired(current, now)
                when {
                    ownerLost -> {
                        pendingTransaction.rollback()
                        finalExecution.copy(
                            snapshot = finalExecution.snapshot.copy(
                                transactionState = SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS,
                                transactionShardNames = emptyList(),
                                ownerToken = current.ownerToken,
                                ownerLeaseExpiresAt = null,
                                pendingCommitExpiresAt = null,
                                errorMessage = "Транзакция автоматически откатана: владелец execution session потерян.",
                            ),
                            pendingTransaction = null,
                            workspaceId = current.workspaceId,
                            ownerSessionId = current.ownerSessionId,
                            ownerToken = current.ownerToken,
                            ownerLost = true,
                            ownerReleaseDeadline = null,
                        )
                    }

                    current.ownerReleaseDeadline != null -> {
                        finalExecution.copy(
                            snapshot = finalExecution.snapshot.copy(
                                ownerToken = current.ownerToken,
                                ownerLeaseExpiresAt = current.ownerReleaseDeadline,
                                pendingCommitExpiresAt = now.plus(pendingCommitTtl),
                            ),
                            workspaceId = current.workspaceId,
                            ownerSessionId = current.ownerSessionId,
                            ownerToken = current.ownerToken,
                            ownerLost = false,
                            ownerReleaseDeadline = current.ownerReleaseDeadline,
                        )
                    }

                    else -> {
                        finalExecution.copy(
                            snapshot = finalExecution.snapshot.copy(
                                ownerToken = current.ownerToken,
                                ownerLeaseExpiresAt = now.plus(ownerLeaseDuration),
                                pendingCommitExpiresAt = now.plus(pendingCommitTtl),
                            ),
                            workspaceId = current.workspaceId,
                            ownerSessionId = current.ownerSessionId,
                            ownerToken = current.ownerToken,
                            ownerLost = false,
                            ownerReleaseDeadline = null,
                        )
                    }
                }
            }
        }

        executionsById[executionId] = updated
        pruneCompletedExecutions()
    }

    fun currentSnapshot(workspaceId: String? = null): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        executionsById.values
            .asSequence()
            .filter { it.workspaceId == normalizedWorkspaceId }
            .maxByOrNull { it.snapshot.startedAt }
            ?.snapshot
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
        val current = requireOwnedExecution(executionId, ownerSessionId, ownerToken, allowReleased = true)
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
            ownerReleaseDeadline = null,
        )
        executionsById[executionId] = updated
        updated.snapshot
    }

    fun releaseOwnership(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        now: Instant,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val current = requireOwnedExecution(executionId, ownerSessionId, ownerToken)
        if (current.snapshot.status != SqlConsoleExecutionStatus.RUNNING &&
            current.snapshot.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT
        ) {
            return current.snapshot
        }
        val releaseDeadline = now.plus(ownerReleaseRecoveryWindow)
        val updated = current.copy(
            snapshot = current.snapshot.copy(
                ownerLeaseExpiresAt = releaseDeadline,
            ),
            ownerReleaseDeadline = releaseDeadline,
        )
        executionsById[executionId] = updated
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
        executionsById[executionId] = updated
        updated.snapshot
    }

    fun updateExecution(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        update: (ActiveExecution) -> ActiveExecution,
    ): SqlConsoleExecutionSnapshot = synchronized(lock) {
        val updated = update(requireOwnedExecution(executionId, ownerSessionId, ownerToken))
        executionsById[executionId] = updated
        pruneCompletedExecutions()
        updated.snapshot
    }

    fun enforceSafetyTimeouts(now: Instant): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        var lastChangedSnapshot: SqlConsoleExecutionSnapshot? = null
        executionsById.keys.toList().forEach { executionId ->
            val current = executionsById[executionId] ?: return@forEach
            var updated = current
            var changed = false

            if (current.snapshot.status == SqlConsoleExecutionStatus.RUNNING &&
                !current.ownerLost &&
                (isLeaseExpired(current.snapshot, now) || isOwnerReleaseExpired(current, now))
            ) {
                updated = current.copy(
                    snapshot = current.snapshot.copy(ownerLeaseExpiresAt = null),
                    ownerLost = true,
                    ownerReleaseDeadline = null,
                )
                changed = true
            }

            if (updated.snapshot.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT &&
                updated.pendingTransaction != null
            ) {
                val timeoutExpired = updated.snapshot.pendingCommitExpiresAt?.let { !now.isBefore(it) } == true
                val ownerLeaseExpired = updated.ownerLost || isLeaseExpired(updated.snapshot, now)
                val ownerReleaseExpired = isOwnerReleaseExpired(updated, now)
                if (timeoutExpired || ownerLeaseExpired || ownerReleaseExpired) {
                    updated.pendingTransaction.rollback()
                    updated = updated.copy(
                        snapshot = updated.snapshot.copy(
                            transactionState = if (timeoutExpired) {
                                SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_TIMEOUT
                            } else {
                                SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS
                            },
                            transactionShardNames = emptyList(),
                            ownerToken = updated.ownerToken,
                            ownerLeaseExpiresAt = null,
                            pendingCommitExpiresAt = null,
                            errorMessage = if (timeoutExpired) {
                                "Транзакция автоматически откатана: истек допустимый TTL ожидания commit."
                            } else {
                                "Транзакция автоматически откатана: владелец execution session потерян."
                            },
                        ),
                        pendingTransaction = null,
                        ownerLost = ownerLeaseExpired || ownerReleaseExpired,
                        ownerReleaseDeadline = null,
                    )
                    changed = true
                }
            }

            if (changed) {
                executionsById[executionId] = updated
                lastChangedSnapshot = updated.snapshot
            }
        }
        pruneCompletedExecutions()
        lastChangedSnapshot ?: currentSnapshot()
    }

    private fun requireExecution(executionId: String): ActiveExecution =
        executionsById[executionId]
            ?: throw UiEntityNotFoundException("Запуск SQL-консоли $executionId не найден.")

    private fun requireOwnedExecution(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        allowReleased: Boolean = false,
    ): ActiveExecution {
        val execution = requireExecution(executionId)
        if (execution.ownerSessionId != ownerSessionId || execution.ownerToken != ownerToken) {
            throw UiStateConflictException("Execution session SQL-консоли больше не принадлежит этой вкладке.")
        }
        if (execution.ownerLost) {
            throw UiStateConflictException("Execution session SQL-консоли уже потеряла владельца.")
        }
        if (!allowReleased && execution.ownerReleaseDeadline != null) {
            throw UiStateConflictException("Execution session SQL-консоли больше не принадлежит активному control-path этой вкладки.")
        }
        return execution
    }

    private fun isLeaseExpired(
        snapshot: SqlConsoleExecutionSnapshot,
        now: Instant,
    ): Boolean = snapshot.ownerLeaseExpiresAt?.let { !now.isBefore(it) } == true

    private fun isOwnerReleaseExpired(
        execution: ActiveExecution,
        now: Instant,
    ): Boolean = execution.ownerReleaseDeadline?.let { !now.isBefore(it) } == true

    private fun pruneCompletedExecutions() {
        val completedIds = executionsById.values
            .filterNot { it.snapshot.status == SqlConsoleExecutionStatus.RUNNING || it.snapshot.transactionState == SqlConsoleExecutionTransactionState.PENDING_COMMIT }
            .sortedBy { it.snapshot.finishedAt ?: it.snapshot.startedAt }
            .map { it.snapshot.id }
        if (completedIds.size <= completedRetentionLimit) {
            return
        }
        completedIds
            .take(completedIds.size - completedRetentionLimit)
            .forEach(executionsById::remove)
    }
}

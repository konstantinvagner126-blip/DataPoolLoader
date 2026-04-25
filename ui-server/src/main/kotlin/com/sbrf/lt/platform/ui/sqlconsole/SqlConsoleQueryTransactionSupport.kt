package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsolePendingTransaction
import com.sbrf.lt.platform.ui.error.UiStateConflictException

internal class SqlConsoleQueryTransactionSupport(
    private val stateSupport: SqlConsoleQueryStateSupport,
) {
    fun commit(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot = finalizeTransaction(
        executionId = executionId,
        ownerSessionId = ownerSessionId,
        ownerToken = ownerToken,
        targetState = SqlConsoleExecutionTransactionState.COMMITTED,
    ) { it.commit() }

    fun rollback(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot = finalizeTransaction(
        executionId = executionId,
        ownerSessionId = ownerSessionId,
        ownerToken = ownerToken,
        targetState = SqlConsoleExecutionTransactionState.ROLLED_BACK,
    ) { it.rollback() }

    private fun finalizeTransaction(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        targetState: SqlConsoleExecutionTransactionState,
        action: (SqlConsolePendingTransaction) -> Unit,
    ): SqlConsoleExecutionSnapshot = stateSupport.updateExecution(
        executionId = executionId,
        ownerSessionId = ownerSessionId,
        ownerToken = ownerToken,
    ) { current ->
        if (current.snapshot.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
            throw UiStateConflictException("Для этого запуска нет незавершенной транзакции.")
        }
        val pendingTransaction = requireNotNull(current.pendingTransaction)
        action(pendingTransaction)
        current.copy(
            snapshot = current.snapshot.copy(
                transactionState = targetState,
                transactionShardNames = emptyList(),
                ownerToken = null,
                ownerLeaseExpiresAt = null,
                pendingCommitExpiresAt = null,
            ),
            pendingTransaction = null,
            ownerLost = false,
            ownerReleaseDeadline = null,
        )
    }
}

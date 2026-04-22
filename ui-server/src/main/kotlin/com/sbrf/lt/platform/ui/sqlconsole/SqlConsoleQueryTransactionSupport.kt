package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsolePendingTransaction
import com.sbrf.lt.platform.ui.error.UiStateConflictException

internal class SqlConsoleQueryTransactionSupport(
    private val stateSupport: SqlConsoleQueryStateSupport,
) {
    fun commit(executionId: String): SqlConsoleExecutionSnapshot = finalizeTransaction(
        executionId = executionId,
        targetState = SqlConsoleExecutionTransactionState.COMMITTED,
    ) { it.commit() }

    fun rollback(executionId: String): SqlConsoleExecutionSnapshot = finalizeTransaction(
        executionId = executionId,
        targetState = SqlConsoleExecutionTransactionState.ROLLED_BACK,
    ) { it.rollback() }

    private fun finalizeTransaction(
        executionId: String,
        targetState: SqlConsoleExecutionTransactionState,
        action: (SqlConsolePendingTransaction) -> Unit,
    ): SqlConsoleExecutionSnapshot = stateSupport.updateExecution(executionId) { current ->
        if (current.snapshot.transactionState != SqlConsoleExecutionTransactionState.PENDING_COMMIT) {
            throw UiStateConflictException("Для этого запуска нет незавершенной транзакции.")
        }
        val pendingTransaction = requireNotNull(current.pendingTransaction)
        action(pendingTransaction)
        current.copy(
            snapshot = current.snapshot.copy(
                transactionState = targetState,
                transactionShardNames = emptyList(),
            ),
            pendingTransaction = null,
        )
    }
}

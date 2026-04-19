package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import java.time.Instant

/**
 * Текущее или завершенное состояние выполнения одного SQL-запроса в SQL-консоли UI.
 */
data class SqlConsoleExecutionSnapshot(
    val id: String,
    val status: SqlConsoleExecutionStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val cancelRequested: Boolean = false,
    val autoCommitEnabled: Boolean = true,
    val transactionState: SqlConsoleExecutionTransactionState = SqlConsoleExecutionTransactionState.NONE,
    val transactionShardNames: List<String> = emptyList(),
    val result: SqlConsoleQueryResult? = null,
    val errorMessage: String? = null,
)

enum class SqlConsoleExecutionTransactionState {
    NONE,
    PENDING_COMMIT,
    COMMITTED,
    ROLLED_BACK,
}

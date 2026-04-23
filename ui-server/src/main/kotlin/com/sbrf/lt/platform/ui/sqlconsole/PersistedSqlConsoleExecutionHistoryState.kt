package com.sbrf.lt.platform.ui.sqlconsole

import java.time.Duration
import java.time.Instant

internal const val DEFAULT_SQL_CONSOLE_EXECUTION_HISTORY_LIMIT = 20

internal data class PersistedSqlConsoleExecutionHistoryState(
    val entries: List<PersistedSqlConsoleExecutionHistoryEntry> = emptyList(),
) {
    fun normalized(limit: Int = DEFAULT_SQL_CONSOLE_EXECUTION_HISTORY_LIMIT): PersistedSqlConsoleExecutionHistoryState =
        copy(
            entries = entries
                .mapNotNull { it.normalizedOrNull() }
                .sortedByDescending { it.startedAt }
                .distinctBy { it.executionId }
                .take(limit),
        )
}

internal data class PersistedSqlConsoleExecutionHistoryEntry(
    val executionId: String,
    val sql: String,
    val selectedSourceNames: List<String> = emptyList(),
    val autoCommitEnabled: Boolean = true,
    val status: String,
    val transactionState: String = SqlConsoleExecutionTransactionState.NONE.name,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val durationMillis: Long? = null,
    val errorMessage: String? = null,
) {
    fun normalizedOrNull(): PersistedSqlConsoleExecutionHistoryEntry? {
        val normalizedExecutionId = executionId.trim()
        val normalizedSql = sql.trim()
        if (normalizedExecutionId.isBlank() || normalizedSql.isBlank()) {
            return null
        }
        val normalizedFinishedAt = finishedAt
        val normalizedDurationMillis = normalizedFinishedAt?.let {
            Duration.between(startedAt, it).toMillis().coerceAtLeast(0L)
        } ?: durationMillis?.coerceAtLeast(0L)
        return copy(
            executionId = normalizedExecutionId,
            sql = normalizedSql,
            selectedSourceNames = selectedSourceNames
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            status = status.trim().uppercase(),
            transactionState = transactionState.trim().uppercase().ifBlank { SqlConsoleExecutionTransactionState.NONE.name },
            finishedAt = normalizedFinishedAt,
            durationMillis = normalizedDurationMillis,
            errorMessage = errorMessage?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

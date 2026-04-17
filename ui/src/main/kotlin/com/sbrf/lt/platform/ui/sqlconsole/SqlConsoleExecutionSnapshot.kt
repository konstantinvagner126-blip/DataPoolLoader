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
    val result: SqlConsoleQueryResult? = null,
    val errorMessage: String? = null,
)

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsolePendingTransaction
import java.time.Instant

/**
 * Активное выполнение SQL-консоли: снимок состояния плюс объект управления отменой.
 */
internal data class ActiveExecution(
    val snapshot: SqlConsoleExecutionSnapshot,
    val control: SqlConsoleExecutionControl,
    val pendingTransaction: SqlConsolePendingTransaction? = null,
    val sql: String,
    val selectedSourceNames: List<String>,
    val workspaceId: String,
    val ownerSessionId: String,
    val ownerToken: String,
    val ownerLost: Boolean = false,
    val ownerReleaseDeadline: Instant? = null,
)

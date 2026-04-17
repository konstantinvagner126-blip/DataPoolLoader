package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl

/**
 * Активное выполнение SQL-консоли: снимок состояния плюс объект управления отменой.
 */
internal data class ActiveExecution(
    val snapshot: SqlConsoleExecutionSnapshot,
    val control: SqlConsoleExecutionControl,
)

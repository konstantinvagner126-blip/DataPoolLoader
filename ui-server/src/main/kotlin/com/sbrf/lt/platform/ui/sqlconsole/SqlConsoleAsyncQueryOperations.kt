package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import java.nio.file.Path

interface SqlConsoleAsyncQueryOperations {
    fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        executionPolicy: SqlConsoleExecutionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
        transactionMode: SqlConsoleTransactionMode = SqlConsoleTransactionMode.AUTO_COMMIT,
        cleanupDir: Path? = null,
    ): SqlConsoleExecutionSnapshot

    fun currentSnapshot(): SqlConsoleExecutionSnapshot?

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot

    fun cancel(executionId: String): SqlConsoleExecutionSnapshot
}

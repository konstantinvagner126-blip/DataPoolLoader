package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Path

interface SqlConsoleAsyncQueryOperations {
    fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        cleanupDir: Path? = null,
    ): SqlConsoleExecutionSnapshot

    fun currentSnapshot(): SqlConsoleExecutionSnapshot?

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot

    fun cancel(executionId: String): SqlConsoleExecutionSnapshot
}

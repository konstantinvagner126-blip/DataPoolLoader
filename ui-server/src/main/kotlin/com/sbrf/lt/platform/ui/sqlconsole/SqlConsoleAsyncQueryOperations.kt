package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import java.nio.file.Path

interface SqlConsoleAsyncQueryOperations {
    fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        workspaceId: String? = null,
        ownerSessionId: String,
        executionPolicy: SqlConsoleExecutionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
        transactionMode: SqlConsoleTransactionMode = SqlConsoleTransactionMode.AUTO_COMMIT,
        cleanupDir: Path? = null,
    ): SqlConsoleExecutionSnapshot

    fun currentSnapshot(workspaceId: String? = null): SqlConsoleExecutionSnapshot?

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot

    fun heartbeat(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot

    fun releaseOwnership(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot

    fun cancel(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot

    fun commit(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot

    fun rollback(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot
}

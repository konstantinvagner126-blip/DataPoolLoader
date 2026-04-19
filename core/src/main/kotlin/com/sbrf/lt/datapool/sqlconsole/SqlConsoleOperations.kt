package com.sbrf.lt.datapool.sqlconsole

import java.nio.file.Path

interface SqlConsoleOperations {
    fun info(): SqlConsoleInfo

    fun updateMaxRowsPerShard(maxRowsPerShard: Int): SqlConsoleInfo

    fun updateSettings(
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): SqlConsoleInfo

    fun executeQuery(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        executionPolicy: SqlConsoleExecutionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
        transactionMode: SqlConsoleTransactionMode = SqlConsoleTransactionMode.AUTO_COMMIT,
        executionControl: SqlConsoleExecutionControl = SqlConsoleExecutionControl(),
    ): SqlConsoleQueryResult

    fun checkConnections(
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
    ): SqlConsoleConnectionCheckResult
}

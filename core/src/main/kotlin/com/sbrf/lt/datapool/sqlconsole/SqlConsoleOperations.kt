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

    fun searchObjects(
        rawQuery: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        maxObjectsPerSource: Int = 30,
    ): SqlConsoleDatabaseObjectSearchResult

    fun inspectObject(
        sourceName: String,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
        credentialsPath: Path?,
    ): SqlConsoleDatabaseObjectInspector
}

interface SqlConsoleTransactionalOperations {
    fun executeQueryRun(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        autoCommitEnabled: Boolean = true,
        executionControl: SqlConsoleExecutionControl = SqlConsoleExecutionControl(),
    ): SqlConsoleExecutionRun
}

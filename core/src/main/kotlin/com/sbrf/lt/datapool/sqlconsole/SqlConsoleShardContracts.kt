package com.sbrf.lt.datapool.sqlconsole

fun interface ShardSqlExecutor {
    fun execute(
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionControl: SqlConsoleExecutionControl,
    ): RawShardExecutionResult
}

interface ShardSqlScriptExecutor {
    fun executeScript(
        shard: ResolvedSqlConsoleShardConfig,
        statements: List<SqlConsoleStatement>,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        executionControl: SqlConsoleExecutionControl,
    ): List<RawShardExecutionResult>
}

interface ShardSqlTransactionalExecutor {
    fun executeScriptInTransaction(
        shard: ResolvedSqlConsoleShardConfig,
        statements: List<SqlConsoleStatement>,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionPolicy: SqlConsoleExecutionPolicy,
        executionControl: SqlConsoleExecutionControl,
    ): TransactionalShardScriptExecution
}

data class TransactionalShardScriptExecution(
    val results: List<RawShardExecutionResult>,
    val pendingTransaction: PendingShardTransaction? = null,
)

interface PendingShardTransaction {
    val shardName: String

    fun commit()

    fun rollback()
}

fun interface ShardConnectionChecker {
    fun check(
        shard: ResolvedSqlConsoleShardConfig,
        queryTimeoutSec: Int?,
    ): RawShardConnectionCheckResult
}

fun interface ShardSqlObjectSearcher {
    fun searchObjects(
        shard: ResolvedSqlConsoleShardConfig,
        rawQuery: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult
}

fun interface ShardSqlObjectInspector {
    fun inspectObject(
        shard: ResolvedSqlConsoleShardConfig,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector
}

fun interface ShardSqlObjectColumnLoader {
    fun loadObjectColumns(
        shard: ResolvedSqlConsoleShardConfig,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): List<SqlConsoleDatabaseObjectColumn>
}

data class ShardSqlObjectSearchResult(
    val objects: List<SqlConsoleDatabaseObject>,
    val truncated: Boolean = false,
)

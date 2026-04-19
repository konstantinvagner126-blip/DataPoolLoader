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

fun interface ShardConnectionChecker {
    fun check(
        shard: ResolvedSqlConsoleShardConfig,
        queryTimeoutSec: Int?,
    ): RawShardConnectionCheckResult
}

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution
import java.time.Duration
import java.util.concurrent.CountDownLatch

internal const val OWNER_SESSION_ID = "owner-session-1"

internal fun sqlConsoleQueryManagerSuccessService() = SqlConsoleService(
    config = SqlConsoleConfig(
        sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
    ),
    executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
        kotlin.test.assertEquals("SELECT", statement.leadingKeyword)
        RawShardExecutionResult(
            shardName = shard.name,
            status = "SUCCESS",
            columns = listOf("id"),
            rows = listOf(mapOf("id" to "1")),
        )
    },
)

internal fun autoCommitBlockingService(releaseExecution: CountDownLatch) = SqlConsoleService(
    config = SqlConsoleConfig(
        sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
    ),
    executor = ShardSqlExecutor { shard, _, _, _, _, control ->
        repeat(100) {
            if (control.isCancelled()) {
                throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
            }
            if (releaseExecution.count == 0L) {
                return@ShardSqlExecutor RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                )
            }
            Thread.sleep(5)
        }
        releaseExecution.await()
        RawShardExecutionResult(
            shardName = shard.name,
            status = "SUCCESS",
            columns = listOf("id"),
            rows = listOf(mapOf("id" to "1")),
        )
    },
)

internal fun manualTransactionService(releaseExecution: CountDownLatch? = null) = SqlConsoleService(
    config = SqlConsoleConfig(
        sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
    ),
    executor = object : ShardSqlExecutor, ShardSqlTransactionalExecutor {
        override fun execute(
            shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
            statement: SqlConsoleStatement,
            fetchSize: Int,
            maxRows: Int,
            queryTimeoutSec: Int?,
            executionControl: SqlConsoleExecutionControl,
        ): RawShardExecutionResult =
            RawShardExecutionResult(
                shardName = shard.name,
                status = "SUCCESS",
                columns = listOf("id"),
                rows = listOf(mapOf("id" to "1")),
            )

        override fun executeScriptInTransaction(
            shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
            statements: List<SqlConsoleStatement>,
            fetchSize: Int,
            maxRows: Int,
            queryTimeoutSec: Int?,
            executionPolicy: SqlConsoleExecutionPolicy,
            executionControl: SqlConsoleExecutionControl,
        ): TransactionalShardScriptExecution {
            releaseExecution?.await()
            return TransactionalShardScriptExecution(
                results = statements.map {
                    RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        affectedRows = 1,
                        message = "ok",
                    )
                },
                pendingTransaction = object : com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction {
                    override val shardName: String = shard.name
                    override fun commit() = Unit
                    override fun rollback() = Unit
                },
            )
        }
    },
)

internal fun waitForCompletion(
    manager: SqlConsoleQueryManager,
    executionId: String,
): SqlConsoleExecutionSnapshot {
    repeat(50) {
        val snapshot = manager.snapshot(executionId)
        if (snapshot.status != SqlConsoleExecutionStatus.RUNNING) {
            return snapshot
        }
        Thread.sleep(20)
    }
    error("SQL console query did not finish in time")
}

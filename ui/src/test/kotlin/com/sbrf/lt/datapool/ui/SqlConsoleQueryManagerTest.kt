package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerTest {

    @Test
    fun `completes query asynchronously`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                    assertEquals("SELECT", statement.leadingKeyword)
                    RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "1")),
                    )
                },
            ),
        )

        val started = manager.startQuery("select 1 as id", null)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, snapshot.status)
        assertNotNull(snapshot.result)
        assertEquals("SELECT", snapshot.result.statementKeyword)
    }

    @Test
    fun `cancels running query`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, control ->
                    repeat(50) {
                        if (control.isCancelled()) {
                            throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
                        }
                        Thread.sleep(10)
                    }
                    RawShardExecutionResult(
                        shardName = "db1",
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "1")),
                    )
                },
            ),
        )

        val started = manager.startQuery("select pg_sleep(10)", null)
        manager.cancel(started.id)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.CANCELLED, snapshot.status)
        assertTrue(snapshot.cancelRequested)
    }

    private fun waitForCompletion(
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
}

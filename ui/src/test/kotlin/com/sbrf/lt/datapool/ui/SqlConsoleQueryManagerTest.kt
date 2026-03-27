package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `keeps successful execution snapshot when shard failed and exposes current snapshot`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                    error("boom")
                },
            ),
        )

        val started = manager.startQuery("select 1", null)
        assertEquals(started.id, manager.currentSnapshot()?.id)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, snapshot.status)
        assertEquals("FAILED", snapshot.result?.shardResults?.single()?.status)
        assertEquals("boom", snapshot.result?.shardResults?.single()?.errorMessage)
    }

    @Test
    fun `rejects concurrent start and cleans up directory`() {
        val cleanupDir = Files.createTempDirectory("sql-console-cleanup")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, control ->
                    repeat(20) {
                        if (control.isCancelled()) {
                            throw SqlConsoleExecutionCancelledException("cancelled")
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

        val started = manager.startQuery("select 1", null, cleanupDir = cleanupDir)
        val error = assertFailsWith<IllegalArgumentException> {
            manager.startQuery("select 2", null)
        }
        assertTrue(error.message!!.contains("уже выполняется запрос"))
        waitForCompletion(manager, started.id)
        assertTrue(!Files.exists(cleanupDir))
    }

    @Test
    fun `throws when snapshot or cancel target is unknown or finished`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                    RawShardExecutionResult(
                        shardName = "db1",
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "1")),
                    )
                },
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            manager.snapshot("missing")
        }

        val started = manager.startQuery("select 1", null)
        val finished = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)

        val error = assertFailsWith<IllegalArgumentException> {
            manager.cancel(started.id)
        }
        assertTrue(error.message!!.contains("уже завершен"))
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

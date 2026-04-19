package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionRun
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerTest {

    @Test
    fun `current snapshot is null before first start and cancel unknown execution fails`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = serviceWithSuccess(),
        )

        assertNull(manager.currentSnapshot())

        val error = assertFailsWith<IllegalArgumentException> {
            manager.cancel("missing")
        }
        assertTrue(error.message!!.contains("не найден"))
    }

    @Test
    fun `completes query asynchronously`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = serviceWithSuccess())

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
    fun `forces stop on first error policy for sql console service`() {
        var observedPolicy: SqlConsoleExecutionPolicy? = null
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = object : SqlConsoleOperations, SqlConsoleTransactionalOperations {
                override fun info(): SqlConsoleInfo = SqlConsoleInfo(
                    configured = true,
                    sourceNames = listOf("db1"),
                    maxRowsPerShard = 200,
                    queryTimeoutSec = null,
                )

                override fun updateMaxRowsPerShard(maxRowsPerShard: Int): SqlConsoleInfo = info()

                override fun updateSettings(
                    maxRowsPerShard: Int,
                    queryTimeoutSec: Int?,
                ): SqlConsoleInfo = info()

                override fun executeQuery(
                    rawSql: String,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    transactionMode: SqlConsoleTransactionMode,
                    executionControl: SqlConsoleExecutionControl,
                ): SqlConsoleQueryResult {
                    observedPolicy = executionPolicy
                    return serviceWithSuccess().executeQuery(
                        rawSql = rawSql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = selectedSourceNames,
                        executionPolicy = executionPolicy,
                        transactionMode = transactionMode,
                        executionControl = executionControl,
                    )
                }

                override fun executeQueryRun(
                    rawSql: String,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                    autoCommitEnabled: Boolean,
                    executionControl: SqlConsoleExecutionControl,
                ): SqlConsoleExecutionRun = SqlConsoleExecutionRun(
                    result = executeQuery(
                        rawSql = rawSql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = selectedSourceNames,
                        executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
                        transactionMode = if (autoCommitEnabled) {
                            SqlConsoleTransactionMode.AUTO_COMMIT
                        } else {
                            SqlConsoleTransactionMode.TRANSACTION_PER_SHARD
                        },
                        executionControl = executionControl,
                    ),
                )

                override fun checkConnections(
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                ): SqlConsoleConnectionCheckResult = SqlConsoleConnectionCheckResult(emptyList())

                override fun searchObjects(
                    rawQuery: String,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                    maxObjectsPerSource: Int,
                ): com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSearchResult =
                    com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSearchResult(
                        query = rawQuery,
                        sourceResults = emptyList(),
                        maxObjectsPerSource = maxObjectsPerSource,
                    )
            },
        )

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            executionPolicy = SqlConsoleExecutionPolicy.CONTINUE_ON_ERROR,
        )
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, snapshot.status)
        assertEquals(SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR, observedPolicy)
    }

    @Test
    fun `keeps pending transaction until commit`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = object : ShardSqlExecutor, com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor {
                    override fun execute(
                        shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                        statement: com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement,
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
                        statements: List<com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement>,
                        fetchSize: Int,
                        maxRows: Int,
                        queryTimeoutSec: Int?,
                        executionPolicy: SqlConsoleExecutionPolicy,
                        executionControl: SqlConsoleExecutionControl,
                    ): com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution =
                        com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution(
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
                },
            ),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, pending.status)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val committed = manager.commit(started.id)

        assertEquals(SqlConsoleExecutionTransactionState.COMMITTED, committed.transactionState)
        assertTrue(committed.transactionShardNames.isEmpty())
    }

    @Test
    fun `does not expose pending commit for read only script when auto commit disabled`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = object : ShardSqlExecutor, com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor {
                    override fun execute(
                        shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                        statement: com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement,
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
                        statements: List<com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement>,
                        fetchSize: Int,
                        maxRows: Int,
                        queryTimeoutSec: Int?,
                        executionPolicy: SqlConsoleExecutionPolicy,
                        executionControl: SqlConsoleExecutionControl,
                    ): com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution =
                        com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution(
                            results = statements.map {
                                RawShardExecutionResult(
                                    shardName = shard.name,
                                    status = "SUCCESS",
                                    columns = listOf("id"),
                                    rows = listOf(mapOf("id" to "1")),
                                    message = "ok",
                                )
                            },
                            pendingTransaction = object : com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction {
                                override val shardName: String = shard.name

                                override fun commit() = Unit

                                override fun rollback() = Unit
                            },
                        )
                },
            ),
        )

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)
        assertEquals(SqlConsoleExecutionTransactionState.NONE, finished.transactionState)
        assertTrue(finished.transactionShardNames.isEmpty())
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

    @Test
    fun `keeps snapshot and cleans up directory when shard execution fails`() {
        val cleanupDir = Files.createTempDirectory("sql-console-failed-cleanup")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sources = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                    throw IllegalStateException("boom")
                },
            ),
        )

        val started = manager.startQuery("select 1", null, cleanupDir = cleanupDir)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, snapshot.status)
        assertEquals("FAILED", snapshot.result?.shardResults?.single()?.status)
        assertEquals("boom", snapshot.result?.shardResults?.single()?.errorMessage)
        assertTrue(!Files.exists(cleanupDir))
    }

    @Test
    fun `marks execution as failed when service throws before shard execution`() {
        val cleanupDir = Files.createTempDirectory("sql-console-invalid-config")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(sources = emptyList()),
            ),
        )

        val started = manager.startQuery("select 1", null, cleanupDir = cleanupDir)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.FAILED, snapshot.status)
        assertFalse(snapshot.cancelRequested)
        assertTrue(snapshot.errorMessage!!.contains("не настроены source-подключения"))
        assertTrue(!Files.exists(cleanupDir))
    }

    private fun serviceWithSuccess() = SqlConsoleService(
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
    )

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

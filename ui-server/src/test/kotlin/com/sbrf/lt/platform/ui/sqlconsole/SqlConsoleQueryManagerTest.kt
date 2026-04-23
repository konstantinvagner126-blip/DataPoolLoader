package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectInspector
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionRun
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceCatalogEntry
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerTest {
    private val ownerSessionId = "owner-session-1"

    @Test
    fun `current snapshot is null before first start and cancel unknown execution fails`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = serviceWithSuccess(),
        )

        assertNull(manager.currentSnapshot())

        val error = assertFailsWith<UiEntityNotFoundException> {
            manager.cancel("missing", ownerSessionId, "missing-token")
        }
        assertTrue(error.message!!.contains("не найден"))
    }

    @Test
    fun `completes query asynchronously`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = serviceWithSuccess())

        val started = manager.startQuery("select 1 as id", null, ownerSessionId = ownerSessionId)
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
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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

        val started = manager.startQuery("select pg_sleep(10)", null, ownerSessionId = ownerSessionId)
        manager.cancel(started.id, ownerSessionId, requireNotNull(started.ownerToken))
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.CANCELLED, snapshot.status)
        assertTrue(snapshot.cancelRequested)
    }

    @Test
    fun `keeps successful execution snapshot when shard failed and exposes current snapshot`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                    error("boom")
                },
            ),
        )

        val started = manager.startQuery("select 1", null, ownerSessionId = ownerSessionId)
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
                    sourceCatalog = listOf(SqlConsoleSourceCatalogEntry(name = "db1")),
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

                override fun inspectObject(
                    sourceName: String,
                    schemaName: String,
                    objectName: String,
                    objectType: SqlConsoleDatabaseObjectType,
                    credentialsPath: Path?,
                ): SqlConsoleDatabaseObjectInspector =
                    SqlConsoleDatabaseObjectInspector(
                        schemaName = schemaName,
                        objectName = objectName,
                        objectType = objectType,
                    )
            },
        )

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
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
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, pending.status)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val committed = manager.commit(started.id, ownerSessionId, requireNotNull(started.ownerToken))

        assertEquals(SqlConsoleExecutionTransactionState.COMMITTED, committed.transactionState)
        assertTrue(committed.transactionShardNames.isEmpty())
    }

    @Test
    fun `does not expose pending commit for read only script when auto commit disabled`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)
        assertEquals(SqlConsoleExecutionTransactionState.NONE, finished.transactionState)
        assertTrue(finished.transactionShardNames.isEmpty())
    }

    @Test
    fun `allows parallel auto commit starts and cleans up both directories`() {
        val firstCleanupDir = Files.createTempDirectory("sql-console-cleanup-a")
        val secondCleanupDir = Files.createTempDirectory("sql-console-cleanup-b")
        val releaseExecution = CountDownLatch(1)
        val startedExecutions = CountDownLatch(2)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, control ->
                    startedExecutions.countDown()
                    releaseExecution.await()
                    if (control.isCancelled()) {
                        throw SqlConsoleExecutionCancelledException("cancelled")
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

        val firstStarted = manager.startQuery(
            "select 1",
            null,
            ownerSessionId = ownerSessionId,
            cleanupDir = firstCleanupDir,
        )
        val secondStarted = manager.startQuery(
            "select 2",
            null,
            ownerSessionId = "owner-session-2",
            cleanupDir = secondCleanupDir,
        )

        startedExecutions.await()
        assertEquals(SqlConsoleExecutionStatus.RUNNING, manager.snapshot(firstStarted.id).status)
        assertEquals(SqlConsoleExecutionStatus.RUNNING, manager.snapshot(secondStarted.id).status)

        releaseExecution.countDown()
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, waitForCompletion(manager, firstStarted.id).status)
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, waitForCompletion(manager, secondStarted.id).status)
        assertTrue(!Files.exists(firstCleanupDir))
        assertTrue(!Files.exists(secondCleanupDir))
    }

    @Test
    fun `rejects second manual transaction while another manual transaction is running`() {
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService(releaseExecution))

        manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        val error = assertFailsWith<UiStateConflictException> {
            manager.startQuery(
                sql = "update demo set flag = false",
                credentialsPath = null,
                ownerSessionId = "owner-session-2",
                transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
            )
        }

        releaseExecution.countDown()
        assertTrue(error.message!!.contains("ручная транзакция"))
    }

    @Test
    fun `rejects second manual transaction when another tab already has pending commit`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val firstStarted = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, firstStarted.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val error = assertFailsWith<UiStateConflictException> {
            manager.startQuery(
                sql = "update demo set flag = false",
                credentialsPath = null,
                ownerSessionId = "owner-session-2",
                transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
            )
        }

        assertEquals(
            "В другой вкладке SQL-консоли есть незавершенная транзакция. Сначала выполните Commit или Rollback в той вкладке. Пока транзакция не завершена, запуск новой ручной транзакции недоступен.",
            error.message,
        )
    }

    @Test
    fun `throws when snapshot or cancel target is unknown or finished`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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

        assertFailsWith<UiEntityNotFoundException> {
            manager.snapshot("missing")
        }

        val started = manager.startQuery("select 1", null, ownerSessionId = ownerSessionId)
        val finished = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)

        val error = assertFailsWith<UiStateConflictException> {
            manager.cancel(started.id, ownerSessionId, requireNotNull(started.ownerToken))
        }
        assertTrue(error.message!!.contains("уже завершен"))
    }

    @Test
    fun `keeps snapshot and cleans up directory when shard execution fails`() {
        val cleanupDir = Files.createTempDirectory("sql-console-failed-cleanup")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = SqlConsoleService(
                config = SqlConsoleConfig(
                    sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
                ),
                executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                    throw IllegalStateException("boom")
                },
            ),
        )

        val started = manager.startQuery("select 1", null, ownerSessionId = ownerSessionId, cleanupDir = cleanupDir)
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
                config = SqlConsoleConfig(sourceCatalog = emptyList()),
            ),
        )

        val started = manager.startQuery("select 1", null, ownerSessionId = ownerSessionId, cleanupDir = cleanupDir)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.FAILED, snapshot.status)
        assertFalse(snapshot.cancelRequested)
        assertTrue(snapshot.errorMessage!!.contains("не настроены source-подключения"))
        assertTrue(!Files.exists(cleanupDir))
    }

    @Test
    fun `rejects commit from another owner session`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val error = assertFailsWith<UiStateConflictException> {
            manager.commit(started.id, "other-owner", requireNotNull(started.ownerToken))
        }
        assertTrue(error.message!!.contains("не принадлежит"))
    }

    @Test
    fun `heartbeat rotates owner token and fences stale token`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = serviceWithSuccess())

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
        )
        val initialToken = requireNotNull(started.ownerToken)

        val rotated = manager.heartbeat(started.id, ownerSessionId, initialToken)
        val rotatedToken = requireNotNull(rotated.ownerToken)

        assertTrue(rotatedToken != initialToken)

        val error = assertFailsWith<UiStateConflictException> {
            manager.cancel(started.id, ownerSessionId, initialToken)
        }
        assertTrue(error.message!!.contains("не принадлежит"))
    }

    @Test
    fun `release pending commit requires heartbeat recovery before commit`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val initialToken = requireNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val released = manager.releaseOwnership(started.id, ownerSessionId, initialToken)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, released.transactionState)

        val releasedCommitError = assertFailsWith<UiStateConflictException> {
            manager.commit(started.id, ownerSessionId, initialToken)
        }
        assertTrue(releasedCommitError.message!!.contains("control-path"))

        val recovered = manager.heartbeat(started.id, ownerSessionId, initialToken)
        val recoveredToken = requireNotNull(recovered.ownerToken)
        val committed = manager.commit(started.id, ownerSessionId, recoveredToken)

        assertEquals(SqlConsoleExecutionTransactionState.COMMITTED, committed.transactionState)
    }

    @Test
    fun `release pending commit auto rollbacks when recovery window expires`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val initialToken = requireNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        manager.releaseOwnership(started.id, ownerSessionId, initialToken)
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()
        val rolledBack = manager.snapshot(started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, rolledBack.transactionState)
        assertTrue(rolledBack.errorMessage!!.contains("владелец"))
    }

    @Test
    fun `auto rollbacks pending commit when ttl expires`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            pendingCommitTtl = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)
        now = now.plusSeconds(6)

        manager.enforceSafetyTimeouts()
        val timedOut = manager.snapshot(started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_TIMEOUT, timedOut.transactionState)
        assertTrue(timedOut.errorMessage!!.contains("TTL"))
    }

    @Test
    fun `auto rollbacks completed manual transaction when owner lease was lost during running`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(releaseExecution),
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()
        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, finished.transactionState)
        assertTrue(finished.errorMessage!!.contains("владелец"))
    }

    @Test
    fun `release running manual transaction causes safe rollback after recovery window`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(releaseExecution),
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = ownerSessionId,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        manager.releaseOwnership(started.id, ownerSessionId, requireNotNull(started.ownerToken))
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()
        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, finished.transactionState)
        assertTrue(finished.errorMessage!!.contains("владелец"))
    }

    @Test
    fun `releasing one workspace execution does not affect another workspace execution`() {
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = autoCommitBlockingService(releaseExecution),
        )

        val first = manager.startQuery(
            sql = "select 1 as first_value",
            credentialsPath = null,
            workspaceId = "workspace-a",
            ownerSessionId = "owner-a",
        )
        val second = manager.startQuery(
            sql = "select 1 as second_value",
            credentialsPath = null,
            workspaceId = "workspace-b",
            ownerSessionId = "owner-b",
        )

        assertEquals(first.id, manager.currentSnapshot("workspace-a")?.id)
        assertEquals(second.id, manager.currentSnapshot("workspace-b")?.id)

        manager.releaseOwnership(first.id, "owner-a", requireNotNull(first.ownerToken))
        val cancelledSecond = manager.cancel(second.id, "owner-b", requireNotNull(second.ownerToken))

        assertTrue(cancelledSecond.cancelRequested)

        releaseExecution.countDown()
        val finishedFirst = waitForCompletion(manager, first.id)
        val finishedSecond = waitForCompletion(manager, second.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finishedFirst.status)
        assertEquals(SqlConsoleExecutionStatus.CANCELLED, finishedSecond.status)
    }

    private fun serviceWithSuccess() = SqlConsoleService(
        config = SqlConsoleConfig(
            sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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

    private fun autoCommitBlockingService(releaseExecution: CountDownLatch) = SqlConsoleService(
        config = SqlConsoleConfig(
            sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
        ),
        executor = ShardSqlExecutor { shard, statement, _, _, _, control ->
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

    private fun manualTransactionService(releaseExecution: CountDownLatch? = null) = SqlConsoleService(
        config = SqlConsoleConfig(
            sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test", "user", "pwd")),
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
            ): com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution {
                releaseExecution?.await()
                return com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution(
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

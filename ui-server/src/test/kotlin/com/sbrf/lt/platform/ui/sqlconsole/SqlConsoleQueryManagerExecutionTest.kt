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
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionRun
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceCatalogEntry
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerExecutionTest {

    @Test
    fun `current snapshot is null before first start and cancel unknown execution fails`() {
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleQueryManagerSuccessService(),
        )

        assertNull(manager.currentSnapshot())

        val error = assertFailsWith<UiEntityNotFoundException> {
            manager.cancel("missing", OWNER_SESSION_ID, "missing-token")
        }
        assertTrue(error.message!!.contains("не найден"))
    }

    @Test
    fun `completes query asynchronously`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = sqlConsoleQueryManagerSuccessService())

        val started = manager.startQuery("select 1 as id", null, ownerSessionId = OWNER_SESSION_ID)
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

        val started = manager.startQuery("select pg_sleep(10)", null, ownerSessionId = OWNER_SESSION_ID)
        manager.cancel(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))
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

        val started = manager.startQuery("select 1", null, ownerSessionId = OWNER_SESSION_ID)
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

                override fun updateSettings(maxRowsPerShard: Int, queryTimeoutSec: Int?): SqlConsoleInfo = info()

                override fun updateConfig(config: SqlConsoleConfig): SqlConsoleInfo = info()

                override fun executeQuery(
                    rawSql: String,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    transactionMode: SqlConsoleTransactionMode,
                    executionControl: SqlConsoleExecutionControl,
                ): SqlConsoleQueryResult {
                    observedPolicy = executionPolicy
                    return sqlConsoleQueryManagerSuccessService().executeQuery(
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
                ): SqlConsoleExecutionRun =
                    SqlConsoleExecutionRun(
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

                override fun checkConnections(credentialsPath: Path?, selectedSourceNames: List<String>): SqlConsoleConnectionCheckResult =
                    SqlConsoleConnectionCheckResult(emptyList())

                override fun searchObjects(
                    rawQuery: String,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                    maxObjectsPerSource: Int,
                ) = com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectSearchResult(
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

                override fun loadObjectColumns(
                    schemaName: String,
                    objectName: String,
                    objectType: SqlConsoleDatabaseObjectType,
                    credentialsPath: Path?,
                    selectedSourceNames: List<String>,
                ) = com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumnLookupResult(
                    schemaName = schemaName,
                    objectName = objectName,
                    objectType = objectType,
                    sourceResults = emptyList(),
                )
            },
        )

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            executionPolicy = SqlConsoleExecutionPolicy.CONTINUE_ON_ERROR,
        )
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, snapshot.status)
        assertEquals(SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR, observedPolicy)
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
            ownerSessionId = OWNER_SESSION_ID,
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

        val started = manager.startQuery("select 1", null, ownerSessionId = OWNER_SESSION_ID)
        val finished = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)

        val error = assertFailsWith<UiStateConflictException> {
            manager.cancel(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))
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

        val started = manager.startQuery("select 1", null, ownerSessionId = OWNER_SESSION_ID, cleanupDir = cleanupDir)
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

        val started = manager.startQuery("select 1", null, ownerSessionId = OWNER_SESSION_ID, cleanupDir = cleanupDir)
        val snapshot = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.FAILED, snapshot.status)
        assertTrue(!snapshot.cancelRequested)
        assertTrue(snapshot.errorMessage!!.contains("не настроены source-подключения"))
        assertTrue(!Files.exists(cleanupDir))
    }
}

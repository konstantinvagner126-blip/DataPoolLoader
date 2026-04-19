package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.ShardConnectionChecker
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearchResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearcher
import com.sbrf.lt.datapool.sqlconsole.ShardSqlScriptExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObject
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumn
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementType
import com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction
import com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleServiceTest {

    @Test
    fun `returns select results separately for each shard`() {
        val credentials = createTempDirectory("sql-console-credentials")
            .resolve("credential.properties")
            .apply {
                writeText(
                    """
                    SHARD1_URL=jdbc:test:one
                    SHARD1_USER=user1
                    SHARD1_PASSWORD=pwd1
                    SHARD2_URL=jdbc:test:two
                    SHARD2_USER=user2
                    SHARD2_PASSWORD=pwd2
                    """.trimIndent(),
                )
            }

        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                maxRowsPerShard = 2,
                queryTimeoutSec = 15,
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "\${SHARD1_URL}", "\${SHARD1_USER}", "\${SHARD1_PASSWORD}"),
                    SqlConsoleSourceConfig("shard2", "\${SHARD2_URL}", "\${SHARD2_USER}", "\${SHARD2_PASSWORD}"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, maxRows, timeout, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                assertEquals(15, timeout)
                when (shard.name) {
                    "shard1" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id", "value"),
                        rows = listOf(
                            mapOf("id" to "1", "value" to "A"),
                            mapOf("id" to "2", "value" to "B"),
                        ),
                        truncated = false,
                    )

                    "shard2" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id", "value"),
                        rows = listOf(
                            mapOf("id" to "3", "value" to "C"),
                            mapOf("id" to "4", "value" to "D"),
                        ).take(maxRows),
                        truncated = true,
                    )

                    else -> error("unexpected shard")
                }
            },
        )

        val response = service.executeQuery("select id, value from demo", credentials)

        assertEquals(SqlConsoleStatementType.RESULT_SET, response.statementType)
        assertEquals("SELECT", response.statementKeyword)
        assertEquals(2, response.shardResults.size)
        assertEquals(2, response.shardResults.first { it.shardName == "shard1" }.rows.size)
        assertEquals("A", response.shardResults.first { it.shardName == "shard1" }.rows.first()["value"])
        assertTrue(response.shardResults.any { it.shardName == "shard2" && it.truncated })
    }

    @Test
    fun `keeps successful shard rows when one shard fails`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                if (shard.name == "shard2") error("boom")
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                    truncated = false,
                )
            },
        )

        val response = service.executeQuery("select id from demo", null)

        assertEquals(SqlConsoleStatementType.RESULT_SET, response.statementType)
        assertEquals(1, response.shardResults.first { it.shardName == "shard1" }.rows.size)
        assertTrue(response.shardResults.any { it.shardName == "shard2" && it.status == "FAILED" })
    }

    @Test
    fun `executes query only for selected sources`() {
        val executed = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                    SqlConsoleSourceConfig("shard3", "jdbc:test:three", "user3", "pwd3"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                executed += shard.name
                assertEquals("SELECT", statement.leadingKeyword)
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to shard.name)),
                )
            },
        )

        val response = service.executeQuery("select id from demo", null, selectedSourceNames = listOf("shard1", "shard3"))

        assertEquals(listOf("shard1", "shard3"), executed)
        assertEquals(listOf("shard1", "shard3"), response.shardResults.map { it.shardName })
    }

    @Test
    fun `supports update statements`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("shard1", shard.name)
                assertEquals("UPDATE", statement.leadingKeyword)
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    affectedRows = 7,
                    message = "UPDATE выполнен успешно.",
                )
            },
        )

        val response = service.executeQuery("update demo set flag = true", null)

        assertEquals(SqlConsoleStatementType.COMMAND, response.statementType)
        assertEquals("UPDATE", response.statementKeyword)
        assertEquals(7, response.shardResults.single().affectedRows)
    }

    @Test
    fun `supports arbitrary command statements`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("DELETE", statement.leadingKeyword)
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    affectedRows = 5,
                    message = "DELETE выполнен успешно.",
                )
            },
        )

        val response = service.executeQuery("delete from demo where archived = true", null)

        assertEquals(SqlConsoleStatementType.COMMAND, response.statementType)
        assertEquals("DELETE", response.statementKeyword)
        assertEquals(5, response.shardResults.single().affectedRows)
    }

    @Test
    fun `executes multiple statements sequentially`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                when (statement.leadingKeyword) {
                    "UPDATE" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        affectedRows = 3,
                        message = "UPDATE выполнен успешно.",
                    )

                    "SELECT" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "${shard.name}-1")),
                    )

                    else -> error("unexpected statement ${statement.leadingKeyword}")
                }
            },
        )

        val response = service.executeQuery("update demo set flag = true; select id from demo", null)

        assertEquals("SCRIPT", response.statementKeyword)
        assertEquals(2, response.statementResults.size)
        assertEquals("UPDATE", response.statementResults[0].statementKeyword)
        assertEquals("SELECT", response.statementResults[1].statementKeyword)
        assertEquals(SqlConsoleStatementType.RESULT_SET, response.statementResults[1].statementType)
        assertEquals("shard1-1", response.statementResults[1].shardResults.first { it.shardName == "shard1" }.rows.first()["id"])
    }

    @Test
    fun `stops next statements for shard after previous failure`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                when {
                    shard.name == "shard2" && statement.leadingKeyword == "UPDATE" -> error("boom")
                    statement.leadingKeyword == "UPDATE" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        affectedRows = 1,
                        message = "UPDATE выполнен успешно.",
                    )

                    statement.leadingKeyword == "SELECT" -> RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to shard.name)),
                    )

                    else -> error("unexpected statement")
                }
            },
        )

        val response = service.executeQuery("update demo set flag = true; select id from demo", null)

        val secondStatement = response.statementResults[1]
        assertEquals("SUCCESS", secondStatement.shardResults.first { it.shardName == "shard1" }.status)
        assertEquals("SKIPPED", secondStatement.shardResults.first { it.shardName == "shard2" }.status)
    }

    @Test
    fun `executes script through transaction executor when transaction mode is enabled`() {
        var observedPolicy: SqlConsoleExecutionPolicy? = null
        var observedTransactionMode: SqlConsoleTransactionMode? = null
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = object : ShardSqlExecutor, ShardSqlScriptExecutor {
                override fun execute(
                    shard: ResolvedSqlConsoleShardConfig,
                    statement: SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: SqlConsoleExecutionControl,
                ): RawShardExecutionResult = error("single statement executor should not be used in transaction mode")

                override fun executeScript(
                    shard: ResolvedSqlConsoleShardConfig,
                    statements: List<SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    transactionMode: SqlConsoleTransactionMode,
                    executionControl: SqlConsoleExecutionControl,
                ): List<RawShardExecutionResult> {
                    observedPolicy = executionPolicy
                    observedTransactionMode = transactionMode
                    return statements.mapIndexed { index, statement ->
                        when (statement.leadingKeyword) {
                            "UPDATE" -> RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                affectedRows = index + 1,
                                message = "UPDATE выполнен успешно.",
                            )

                            "SELECT" -> RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                columns = listOf("id"),
                                rows = listOf(mapOf("id" to "${shard.name}-${index + 1}")),
                            )

                            else -> error("unexpected statement ${statement.leadingKeyword}")
                        }
                    }
                }
            },
        )

        val response = service.executeQuery(
            rawSql = "update demo set flag = true; select id from demo",
            credentialsPath = null,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        assertEquals(SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR, observedPolicy)
        assertEquals(SqlConsoleTransactionMode.TRANSACTION_PER_SHARD, observedTransactionMode)
        assertEquals(2, response.statementResults.size)
        assertEquals("UPDATE", response.statementResults[0].statementKeyword)
        assertEquals("SELECT", response.statementResults[1].statementKeyword)
        assertEquals(
            "shard2-2",
            response.statementResults[1].shardResults.first { it.shardName == "shard2" }.rows.first()["id"],
        )
    }

    @Test
    fun `returns pending transaction when auto commit disabled`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = object : ShardSqlExecutor, ShardSqlTransactionalExecutor {
                override fun execute(
                    shard: ResolvedSqlConsoleShardConfig,
                    statement: SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: SqlConsoleExecutionControl,
                ): RawShardExecutionResult = error("single statement executor should not be used")

                override fun executeScriptInTransaction(
                    shard: ResolvedSqlConsoleShardConfig,
                    statements: List<SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    executionControl: SqlConsoleExecutionControl,
                ): TransactionalShardScriptExecution =
                    TransactionalShardScriptExecution(
                        results = statements.map {
                            RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                affectedRows = 1,
                                message = "ok",
                            )
                        },
                        pendingTransaction = object : PendingShardTransaction {
                            override val shardName: String = shard.name

                            override fun commit() = Unit

                            override fun rollback() = Unit
                        },
                    )
            },
        )

        val run = service.executeQueryRun(
            rawSql = "update demo set flag = true",
            credentialsPath = null,
            autoCommitEnabled = false,
        )

        assertTrue(run.pendingTransaction != null)
        assertEquals(listOf("shard1"), run.pendingTransaction?.shardNames)
    }

    @Test
    fun `does not keep pending transaction for read only script when auto commit disabled`() {
        var commitCalls = 0
        var rollbackCalls = 0
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = object : ShardSqlExecutor, ShardSqlTransactionalExecutor {
                override fun execute(
                    shard: ResolvedSqlConsoleShardConfig,
                    statement: SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: SqlConsoleExecutionControl,
                ): RawShardExecutionResult = error("single statement executor should not be used")

                override fun executeScriptInTransaction(
                    shard: ResolvedSqlConsoleShardConfig,
                    statements: List<SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    executionControl: SqlConsoleExecutionControl,
                ): TransactionalShardScriptExecution =
                    TransactionalShardScriptExecution(
                        results = statements.map {
                            RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                columns = listOf("id"),
                                rows = listOf(mapOf("id" to "1")),
                                message = "ok",
                            )
                        },
                        pendingTransaction = object : PendingShardTransaction {
                            override val shardName: String = shard.name

                            override fun commit() {
                                commitCalls += 1
                            }

                            override fun rollback() {
                                rollbackCalls += 1
                            }
                        },
                    )
            },
        )

        val run = service.executeQueryRun(
            rawSql = "select 1 as id",
            credentialsPath = null,
            autoCommitEnabled = false,
        )

        assertNull(run.pendingTransaction)
        assertEquals(1, commitCalls)
        assertEquals(0, rollbackCalls)
    }

    @Test
    fun `rejects continue on error in transaction mode`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = object : ShardSqlExecutor, ShardSqlScriptExecutor {
                override fun execute(
                    shard: ResolvedSqlConsoleShardConfig,
                    statement: SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: SqlConsoleExecutionControl,
                ): RawShardExecutionResult = error("unexpected single-statement path")

                override fun executeScript(
                    shard: ResolvedSqlConsoleShardConfig,
                    statements: List<SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: SqlConsoleExecutionPolicy,
                    transactionMode: SqlConsoleTransactionMode,
                    executionControl: SqlConsoleExecutionControl,
                ): List<RawShardExecutionResult> = error("unexpected transaction execution")
            },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            service.executeQuery(
                rawSql = "update demo set flag = true; select id from demo",
                credentialsPath = null,
                executionPolicy = SqlConsoleExecutionPolicy.CONTINUE_ON_ERROR,
                transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
            )
        }

        assertTrue(error.message!!.contains("Транзакционный режим"))
    }

    @Test
    fun `exposes timeout in info`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                queryTimeoutSec = 45,
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        assertEquals(45, service.info().queryTimeoutSec)
    }

    @Test
    fun `updates max rows per shard at runtime`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                maxRowsPerShard = 200,
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val updated = service.updateMaxRowsPerShard(350)

        assertEquals(350, updated.maxRowsPerShard)
        assertEquals(350, service.info().maxRowsPerShard)
    }

    @Test
    fun `updates timeout at runtime`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                maxRowsPerShard = 200,
                queryTimeoutSec = 45,
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val updated = service.updateSettings(maxRowsPerShard = 280, queryTimeoutSec = 90)

        assertEquals(280, updated.maxRowsPerShard)
        assertEquals(90, updated.queryTimeoutSec)
        assertEquals(90, service.info().queryTimeoutSec)
    }

    @Test
    fun `checks connections for all configured sources`() {
        val checked = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                queryTimeoutSec = 12,
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            connectionChecker = ShardConnectionChecker { shard, timeout ->
                checked += shard.name
                assertEquals(12, timeout)
                if (shard.name == "shard2") {
                    error("boom")
                }
                RawShardConnectionCheckResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    message = "ok",
                )
            },
        )

        val result = service.checkConnections(credentialsPath = null)

        assertEquals(listOf("shard1", "shard2"), checked)
        assertEquals("SUCCESS", result.sourceResults.first { it.shardName == "shard1" }.status)
        assertEquals("FAILED", result.sourceResults.first { it.shardName == "shard2" }.status)
    }

    @Test
    fun `connection check returns failed status when placeholder is not resolved`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "\${SHARD1_URL}", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            connectionChecker = ShardConnectionChecker { shard, _ ->
                RawShardConnectionCheckResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    message = "ok",
                )
            },
        )

        val result = service.checkConnections(credentialsPath = null)

        assertEquals("FAILED", result.sourceResults.first { it.shardName == "shard1" }.status)
        assertTrue(result.sourceResults.first { it.shardName == "shard1" }.errorMessage!!.contains("SHARD1_URL"))
        assertEquals("SUCCESS", result.sourceResults.first { it.shardName == "shard2" }.status)
    }

    @Test
    fun `searches database objects only for selected sources`() {
        val searched = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(
                    SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1"),
                    SqlConsoleSourceConfig("shard2", "jdbc:test:two", "user2", "pwd2"),
                ),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
            objectSearcher = ShardSqlObjectSearcher { shard, rawQuery, maxObjects ->
                searched += shard.name
                assertEquals("offer", rawQuery)
                assertEquals(30, maxObjects)
                ShardSqlObjectSearchResult(
                    objects = listOf(
                        SqlConsoleDatabaseObject(
                            schemaName = "public",
                            objectName = "${shard.name}_offer",
                            objectType = SqlConsoleDatabaseObjectType.TABLE,
                            columns = listOf(
                                SqlConsoleDatabaseObjectColumn(
                                    name = "id",
                                    type = "bigint",
                                    nullable = false,
                                ),
                            ),
                            indexNames = listOf("${shard.name}_offer_idx"),
                        ),
                    ),
                )
            },
        )

        val result = service.searchObjects(
            rawQuery = "offer",
            credentialsPath = null,
            selectedSourceNames = listOf("shard2"),
        )

        assertEquals(listOf("shard2"), searched)
        assertEquals(1, result.sourceResults.size)
        assertEquals("shard2", result.sourceResults.single().sourceName)
        assertEquals("public", result.sourceResults.single().objects.single().schemaName)
    }
}

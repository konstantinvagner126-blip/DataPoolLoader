package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlScriptExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleServiceExecutionTest {

    @Test
    fun `returns select results separately for each shard`() {
        val credentials = createSqlConsoleCredentials(
            "SHARD1_URL" to "jdbc:test:one",
            "SHARD1_USER" to "user1",
            "SHARD1_PASSWORD" to "pwd1",
            "SHARD2_URL" to "jdbc:test:two",
            "SHARD2_USER" to "user2",
            "SHARD2_PASSWORD" to "pwd2",
        )
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                maxRowsPerShard = 2,
                queryTimeoutSec = 15,
                sourceCatalog = listOf(
                    com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig("shard1", "\${SHARD1_URL}", "\${SHARD1_USER}", "\${SHARD1_PASSWORD}"),
                    com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig("shard2", "\${SHARD2_URL}", "\${SHARD2_USER}", "\${SHARD2_PASSWORD}"),
                ),
            ),
            executor = ShardSqlExecutor { shard, statement, _, maxRows, timeout, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                assertEquals(15, timeout)
                when (shard.name) {
                    "shard1" -> resultSetResult(
                        shardName = shard.name,
                        columns = listOf("id", "value"),
                        rows = listOf(
                            mapOf("id" to "1", "value" to "A"),
                            mapOf("id" to "2", "value" to "B"),
                        ),
                    )

                    "shard2" -> resultSetResult(
                        shardName = shard.name,
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
        assertEquals("A", response.shardResults.first { it.shardName == "shard1" }.rows.first()["value"])
        assertTrue(response.shardResults.any { it.shardName == "shard2" && it.truncated })
    }

    @Test
    fun `keeps successful shard rows when one shard fails`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                if (shard.name == "shard2") error("boom")
                resultSetResult(shard.name, listOf("id"), listOf(mapOf("id" to "1")))
            },
        )

        val response = service.executeQuery("select id from demo", null)

        assertEquals(SqlConsoleStatementType.RESULT_SET, response.statementType)
        assertEquals(1, response.shardResults.first { it.shardName == "shard1" }.rows.size)
        assertTrue(response.shardResults.any { it.shardName == "shard2" && it.status == "FAILED" })
    }

    @Test
    fun `marks shard as unavailable on connection-level SQL failure`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
            executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                throw java.sql.SQLException("connection refused", "08001")
            },
        )

        val response = service.executeQuery("select 1", null)

        assertEquals(SqlConsoleConnectionState.UNAVAILABLE, response.shardResults.single().connectionState)
    }

    @Test
    fun `keeps shard available on SQL-level failure`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
            executor = ShardSqlExecutor { _, _, _, _, _, _ ->
                throw java.sql.SQLException("syntax error at or near SELECT", "42601")
            },
        )

        val response = service.executeQuery("select from demo", null)

        assertEquals(SqlConsoleConnectionState.AVAILABLE, response.shardResults.single().connectionState)
    }

    @Test
    fun `executes query only for selected sources`() {
        val executed = mutableListOf<String>()
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sourceCatalog = listOf(testSource("shard1"), testSource("shard2"), testSource("shard3")),
            ),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                executed += shard.name
                assertEquals("SELECT", statement.leadingKeyword)
                resultSetResult(shard.name, listOf("id"), listOf(mapOf("id" to shard.name)))
            },
        )

        val response = service.executeQuery("select id from demo", null, selectedSourceNames = listOf("shard1", "shard3"))

        assertEquals(listOf("shard1", "shard3"), executed)
        assertEquals(listOf("shard1", "shard3"), response.shardResults.map { it.shardName })
    }

    @Test
    fun `supports update statements`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("shard1", shard.name)
                assertEquals("UPDATE", statement.leadingKeyword)
                commandResult(shard.name, affectedRows = 7, message = "UPDATE выполнен успешно.")
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("DELETE", statement.leadingKeyword)
                commandResult(shard.name, affectedRows = 5, message = "DELETE выполнен успешно.")
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                when (statement.leadingKeyword) {
                    "UPDATE" -> commandResult(shard.name, affectedRows = 3, message = "UPDATE выполнен успешно.")
                    "SELECT" -> resultSetResult(
                        shard.name,
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                when {
                    shard.name == "shard2" && statement.leadingKeyword == "UPDATE" -> error("boom")
                    statement.leadingKeyword == "UPDATE" -> commandResult(shard.name, affectedRows = 1, message = "UPDATE выполнен успешно.")
                    statement.leadingKeyword == "SELECT" -> resultSetResult(shard.name, listOf("id"), listOf(mapOf("id" to shard.name)))
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"), testSource("shard2"))),
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
                            "UPDATE" -> commandResult(shard.name, affectedRows = index + 1, message = "UPDATE выполнен успешно.")
                            "SELECT" -> resultSetResult(
                                shard.name,
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
        assertEquals("shard2-2", response.statementResults[1].shardResults.first { it.shardName == "shard2" }.rows.first()["id"])
    }

    @Test
    fun `returns pending transaction when auto commit disabled`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
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
                            commandResult(shard.name, affectedRows = 1, message = "ok")
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
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
                            resultSetResult(shard.name, columns = listOf("id"), rows = listOf(mapOf("id" to "1"))).copy(message = "ok")
                        },
                        pendingTransaction = object : PendingShardTransaction {
                            override val shardName: String = shard.name
                            override fun commit() { commitCalls += 1 }
                            override fun rollback() { rollbackCalls += 1 }
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
            config = SqlConsoleConfig(sourceCatalog = listOf(testSource("shard1"))),
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
}

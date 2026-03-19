package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementType
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `rejects multiple statements`() {
        val service = SqlConsoleService(
            config = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user1", "pwd1")),
            ),
            executor = ShardSqlExecutor { _, _, _, _, _, _ -> error("should not be called") },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            service.executeQuery("select 1; select 2", null)
        }

        assertEquals("В SQL-консоли разрешен только один SQL-запрос за запуск.", error.message)
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
}

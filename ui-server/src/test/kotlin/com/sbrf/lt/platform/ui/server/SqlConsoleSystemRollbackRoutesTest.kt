package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement
import com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlConsoleSystemRollbackRoutesTest {

    @Test
    fun `system rollback terminal route actions return conflicts`() = testApplication {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = manualTransactionSqlConsoleService(uiConfig.sqlConsole)
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
            pendingCommitTtl = Duration.ofSeconds(5),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val (ttlExecutionId, ttlOwnerToken) = startPendingManualTransaction(
            workspaceId = "workspace-ttl-actions",
            ownerSessionId = "tab-ttl-actions",
            sql = "update demo set ttl_action_flag = true",
        )
        now = now.plusSeconds(6)
        queryManager.enforceSafetyTimeouts()
        val ttlSnapshot = client.get("/api/sql-console/query/$ttlExecutionId").bodyAsText()
        assertFinalRouteControlPathCleared(ttlSnapshot, "ROLLED_BACK_BY_TIMEOUT")
        assertTerminalActionsConflict(
            executionId = ttlExecutionId,
            ownerSessionId = "tab-ttl-actions",
            ownerToken = ttlOwnerToken,
            ownerLost = false,
        )

        val (ownerLossExecutionId, ownerLossOwnerToken) = startPendingManualTransaction(
            workspaceId = "workspace-owner-loss-actions",
            ownerSessionId = "tab-owner-loss-actions",
            sql = "update demo set owner_loss_action_flag = true",
        )
        val released = client.post("/api/sql-console/query/$ownerLossExecutionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-owner-loss-actions","ownerToken":"$ownerLossOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, released.status)
        now = now.plusSeconds(4)
        queryManager.enforceSafetyTimeouts()
        val ownerLossSnapshot = client.get("/api/sql-console/query/$ownerLossExecutionId").bodyAsText()
        assertFinalRouteControlPathCleared(ownerLossSnapshot, "ROLLED_BACK_BY_OWNER_LOSS")
        assertTerminalActionsConflict(
            executionId = ownerLossExecutionId,
            ownerSessionId = "tab-owner-loss-actions",
            ownerToken = ownerLossOwnerToken,
            ownerLost = true,
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.startPendingManualTransaction(
        workspaceId: String,
        ownerSessionId: String,
        sql: String,
    ): Pair<String, String> {
        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"$sql","selectedSourceNames":["shard1"],"workspaceId":"$workspaceId","ownerSessionId":"$ownerSessionId","transactionMode":"TRANSACTION_PER_SHARD"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        val executionId = startedBody.jsonStringField("id")
        assertNotNull(executionId)
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(ownerToken)

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains(""""transactionState":"PENDING_COMMIT"""")) {
                return executionId to ownerToken
            }
            Thread.sleep(25)
        }
        error("manual SQL execution did not reach pending commit in time")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.assertTerminalActionsConflict(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
        ownerLost: Boolean,
    ) {
        val ownerLostMessage = if (ownerLost) "потеряла владельца" else null
        assertConflict(
            path = "/api/sql-console/query/$executionId/heartbeat",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
            expectedMessagePart = ownerLostMessage ?: "больше не требует heartbeat",
        )
        assertConflict(
            path = "/api/sql-console/query/$executionId/release",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
            expectedMessagePart = ownerLostMessage ?: "control-path",
        )
        assertConflict(
            path = "/api/sql-console/query/$executionId/cancel",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
            expectedMessagePart = ownerLostMessage ?: "уже завершен",
        )
        assertConflict(
            path = "/api/sql-console/query/$executionId/commit",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
            expectedMessagePart = ownerLostMessage ?: "нет незавершенной транзакции",
        )
        assertConflict(
            path = "/api/sql-console/query/$executionId/rollback",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
            expectedMessagePart = ownerLostMessage ?: "нет незавершенной транзакции",
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.assertConflict(
        path: String,
        ownerSessionId: String,
        ownerToken: String,
        expectedMessagePart: String,
    ) {
        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"$ownerSessionId","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(expectedMessagePart), body)
    }

    private fun manualTransactionSqlConsoleService(config: SqlConsoleConfig): SqlConsoleService =
        SqlConsoleService(
            config = config,
            executor = object : ShardSqlExecutor, ShardSqlTransactionalExecutor {
                override fun execute(
                    shard: ResolvedSqlConsoleShardConfig,
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

    private fun assertFinalRouteControlPathCleared(body: String, expectedTransactionState: String) {
        assertTrue(body.contains(""""transactionState":"$expectedTransactionState""""), body)
        assertTrue(body.jsonFieldIsNullOrMissing("ownerToken"), body)
        assertTrue(body.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), body)
        assertTrue(body.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), body)
    }

    private fun String.jsonFieldIsNullOrMissing(name: String): Boolean {
        val node = createUiServerObjectMapper().readTree(this)
        return !node.has(name) || node.get(name).isNull
    }

    private fun String.jsonStringField(name: String): String? =
        Regex(""""$name":"([^"]+)"""").find(this)?.groupValues?.get(1)
}

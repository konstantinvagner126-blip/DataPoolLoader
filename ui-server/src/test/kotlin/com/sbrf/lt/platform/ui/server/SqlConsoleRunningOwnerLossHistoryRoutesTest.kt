package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.autoCommitBlockingService
import com.sbrf.lt.platform.ui.sqlconsole.manualTransactionService
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
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlConsoleRunningOwnerLossHistoryRoutesTest {

    @Test
    fun `history route exposes running owner-loss rollback terminal state`() = testApplication {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = manualTransactionService(releaseExecution)
        val historyService = SqlConsoleExecutionHistoryService(storageDir)
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            executionHistoryService = historyService,
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
                sqlConsoleExecutionHistoryService = historyService,
            )
        }

        val workspaceId = "workspace-running-owner-loss-history-route"
        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"update demo set flag = true","selectedSourceNames":["db1"],"workspaceId":"$workspaceId","ownerSessionId":"tab-running-owner-loss-history-route","transactionMode":"TRANSACTION_PER_SHARD"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val executionId = started.bodyAsText().jsonStringField("id")
        assertNotNull(executionId)

        now = now.plusSeconds(6)
        queryManager.enforceSafetyTimeouts()
        releaseExecution.countDown()

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains(""""transactionState":"ROLLED_BACK_BY_OWNER_LOSS"""")) {
                assertHistoryRouteState(
                    workspaceId = workspaceId,
                    executionId = executionId,
                    transactionState = "ROLLED_BACK_BY_OWNER_LOSS",
                )
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("manual SQL execution did not expose owner-loss rollback in time")
    }

    @Test
    fun `history route exposes released auto commit owner-loss as successful completion`() = testApplication {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("db1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = autoCommitBlockingService(releaseExecution)
        val historyService = SqlConsoleExecutionHistoryService(storageDir)
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            executionHistoryService = historyService,
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
                sqlConsoleExecutionHistoryService = historyService,
            )
        }

        val workspaceId = "workspace-running-release-autocommit-history-route"
        val ownerSessionId = "tab-running-release-autocommit-history-route"
        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"select pg_sleep(10)","selectedSourceNames":["db1"],"workspaceId":"$workspaceId","ownerSessionId":"$ownerSessionId"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        val executionId = startedBody.jsonStringField("id")
        assertNotNull(executionId)
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(ownerToken)

        val released = client.post("/api/sql-console/query/$executionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"$ownerSessionId","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, released.status)

        now = now.plusSeconds(4)
        queryManager.enforceSafetyTimeouts()
        releaseExecution.countDown()

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains(""""status":"SUCCESS"""")) {
                assertHistoryRouteState(
                    workspaceId = workspaceId,
                    executionId = executionId,
                    status = "SUCCESS",
                    transactionState = "NONE",
                )
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("released auto-commit SQL execution did not complete successfully in time")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.assertHistoryRouteState(
        workspaceId: String,
        executionId: String,
        status: String? = null,
        transactionState: String,
    ) {
        val historyResponse = client.get("/api/sql-console/history?workspaceId=$workspaceId")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        val historyBody = historyResponse.bodyAsText()
        assertTrue(historyBody.contains(""""executionId":"$executionId""""), historyBody)
        status?.let {
            assertTrue(historyBody.contains(""""status":"$it""""), historyBody)
        }
        assertTrue(historyBody.contains(""""transactionState":"$transactionState""""), historyBody)
        assertEquals(
            1,
            Regex(""""executionId":"${Regex.escape(executionId)}"""").findAll(historyBody).count(),
            historyBody,
        )
        assertFalse(historyBody.contains(""""transactionState":"PENDING_COMMIT""""), historyBody)
        if (transactionState != "ROLLED_BACK_BY_OWNER_LOSS") {
            assertFalse(historyBody.contains(""""transactionState":"ROLLED_BACK_BY_OWNER_LOSS""""), historyBody)
        }
    }

    private fun String.jsonStringField(name: String): String? =
        Regex(""""$name":"([^"]+)"""").find(this)?.groupValues?.get(1)
}

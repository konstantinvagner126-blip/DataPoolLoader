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
import kotlin.test.assertTrue

class SqlConsoleRunningOwnerLossRoutesTest {

    @Test
    fun `owner-lost running query route clears control path metadata`() = testApplication {
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
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"select pg_sleep(10)","selectedSourceNames":["db1"],"workspaceId":"workspace-running-owner-loss-route","ownerSessionId":"tab-running-owner-loss-route"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        val executionId = startedBody.jsonStringField("id")
        assertNotNull(executionId)
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(ownerToken)

        val running = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(running.contains(""""id":"$executionId""""), running)
        assertTrue(running.contains(""""status":"RUNNING""""), running)
        assertTrue(running.jsonFieldIsNullOrMissing("ownerToken"), running)
        assertNotNull(running.jsonStringField("ownerLeaseExpiresAt"))

        now = now.plusSeconds(6)
        queryManager.enforceSafetyTimeouts()

        val ownerLost = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(ownerLost.contains(""""id":"$executionId""""), ownerLost)
        assertTrue(ownerLost.contains(""""status":"RUNNING""""), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerToken"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), ownerLost)

        val heartbeat = client.post("/api/sql-console/query/$executionId/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-running-owner-loss-route","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, heartbeat.status)
        assertTrue(heartbeat.bodyAsText().contains("потеряла владельца"))

        val release = client.post("/api/sql-console/query/$executionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-running-owner-loss-route","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, release.status)
        assertTrue(release.bodyAsText().contains("потеряла владельца"))

        val cancel = client.post("/api/sql-console/query/$executionId/cancel") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-running-owner-loss-route","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, cancel.status)
        assertTrue(cancel.bodyAsText().contains("потеряла владельца"))

        releaseExecution.countDown()
        repeat(20) {
            val completed = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (completed.contains(""""status":"SUCCESS"""")) {
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerToken"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), completed)
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("SQL execution did not complete after releasing test latch")
    }

    @Test
    fun `released running manual transaction route rolls back after recovery window`() = testApplication {
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

        val workspaceId = "workspace-running-release-owner-loss-route"
        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"update demo set flag = true","selectedSourceNames":["db1"],"workspaceId":"$workspaceId","ownerSessionId":"tab-running-release-owner-loss-route","transactionMode":"TRANSACTION_PER_SHARD"}""",
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
            setBody("""{"ownerSessionId":"tab-running-release-owner-loss-route","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, released.status)

        now = now.plusSeconds(4)
        queryManager.enforceSafetyTimeouts()

        val ownerLost = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(ownerLost.contains(""""id":"$executionId""""), ownerLost)
        assertTrue(ownerLost.contains(""""status":"RUNNING""""), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerToken"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), ownerLost)

        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/heartbeat",
            ownerSessionId = "tab-running-release-owner-loss-route",
            ownerToken = ownerToken,
        )
        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/release",
            ownerSessionId = "tab-running-release-owner-loss-route",
            ownerToken = ownerToken,
        )
        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/cancel",
            ownerSessionId = "tab-running-release-owner-loss-route",
            ownerToken = ownerToken,
        )

        releaseExecution.countDown()
        repeat(20) {
            val completed = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (completed.contains(""""transactionState":"ROLLED_BACK_BY_OWNER_LOSS"""")) {
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerToken"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), completed)
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("released manual SQL execution did not roll back after owner loss")
    }

    @Test
    fun `released running auto commit route completes success after recovery window`() = testApplication {
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
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val ownerSessionId = "tab-running-release-autocommit-route"
        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"select pg_sleep(10)","selectedSourceNames":["db1"],"workspaceId":"workspace-running-release-autocommit-route","ownerSessionId":"$ownerSessionId"}""",
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

        val ownerLost = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(ownerLost.contains(""""id":"$executionId""""), ownerLost)
        assertTrue(ownerLost.contains(""""status":"RUNNING""""), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerToken"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), ownerLost)
        assertTrue(ownerLost.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), ownerLost)

        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/heartbeat",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        )
        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/release",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        )
        assertOwnerLostActionConflict(
            path = "/api/sql-console/query/$executionId/cancel",
            ownerSessionId = ownerSessionId,
            ownerToken = ownerToken,
        )

        releaseExecution.countDown()
        repeat(20) {
            val completed = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (completed.contains(""""status":"SUCCESS"""")) {
                assertTrue(completed.contains(""""transactionState":"NONE""""), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerToken"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), completed)
                assertTrue(completed.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), completed)
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("released auto-commit SQL execution did not complete successfully")
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.assertOwnerLostActionConflict(
        path: String,
        ownerSessionId: String,
        ownerToken: String,
    ) {
        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"$ownerSessionId","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(response.bodyAsText().contains("потеряла владельца"))
    }

    private fun String.jsonFieldIsNullOrMissing(name: String): Boolean {
        val node = createUiServerObjectMapper().readTree(this)
        return !node.has(name) || node.get(name).isNull
    }

    private fun String.jsonStringField(name: String): String? =
        Regex(""""$name":"([^"]+)"""").find(this)?.groupValues?.get(1)
}

package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.InMemoryDatabaseRunStore
import com.sbrf.lt.platform.ui.run.StubDatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.run.StubUiCredentialsProvider
import com.sbrf.lt.platform.ui.run.sampleRunSummary
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseModuleRunsRecoveryServerTest {

    @Test
    fun `module runs api exposes recovered orphan db run as failed after startup`() = testApplication {
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-db-run-recovery-server-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = object : UiRuntimeContextService() {
            override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                testRuntimeContext(UiModuleStoreMode.DATABASE)
        }
        val moduleStore = StubDatabaseModuleRegistryOperations(moduleCode = "db-demo", moduleTitle = "DB Demo")
        val runStore = InMemoryDatabaseRunStore(
            mutableMapOf(
                "db-demo" to mutableListOf(
                    sampleRunSummary(runId = "run-orphan", status = "RUNNING"),
                ),
            ),
        )
        val runService = DatabaseModuleRunService(
            databaseModuleStore = moduleStore,
            executionSource = DatabaseModuleExecutionSource(
                connectionProvider = DatabaseConnectionProvider { error("execution source must not be used") },
            ),
            runExecutionStore = runStore,
            runQueryStore = runStore,
            credentialsProvider = StubUiCredentialsProvider(),
        )

        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleRunService = runService,
                databaseModuleBackend = DatabaseModuleBackend(moduleStore),
            )
        }

        val sessionResponse = client.get("/api/module-runs/database/db-demo")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        assertTrue(sessionResponse.bodyAsText().contains("\"moduleTitle\":\"DB Demo\""))

        val historyResponse = client.get("/api/module-runs/database/db-demo/runs?limit=1")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        val historyBody = historyResponse.bodyAsText()
        assertTrue(historyBody.contains("\"activeRunId\":null"))
        assertTrue(historyBody.contains("\"runId\":\"run-orphan\""))
        assertTrue(historyBody.contains("\"status\":\"FAILED\""))
        assertTrue(historyBody.contains("восстановлен как FAILED"))

        val detailsResponse = client.get("/api/module-runs/database/db-demo/runs/run-orphan")
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        val detailsBody = detailsResponse.bodyAsText()
        assertTrue(detailsBody.contains("\"runId\":\"run-orphan\""))
        assertTrue(detailsBody.contains("\"status\":\"FAILED\""))
        assertTrue(detailsBody.contains("восстановлен как FAILED"))

        assertEquals(listOf("run-orphan"), runStore.markedFailedRunIds)
    }
}

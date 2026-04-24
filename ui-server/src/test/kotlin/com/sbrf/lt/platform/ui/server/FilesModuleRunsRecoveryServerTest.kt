package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.PersistedRunState
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.RunStateStore
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesModuleRunsRecoveryServerTest {

    @Test
    fun `module runs api exposes recovered interrupted files run as failed after restart`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-files-run-recovery-server-")
        RunStateStore(storageDir).save(
            PersistedRunState(
                history = listOf(
                    UiRunSnapshot(
                        id = "run-interrupted",
                        moduleId = "demo-app",
                        moduleTitle = "Demo App",
                        status = ExecutionStatus.RUNNING,
                        startedAt = Instant.parse("2026-04-24T00:00:00Z"),
                    ),
                ),
            ),
        )
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = uiConfig,
        )

        application {
            uiModule(
                uiConfig = uiConfig,
                moduleRegistry = registry,
                runManager = runManager,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val sessionResponse = client.get("/api/module-runs/files/demo-app")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        assertTrue(sessionResponse.bodyAsText().contains("\"moduleTitle\":\"Demo App\""))

        val historyResponse = client.get("/api/module-runs/files/demo-app/runs")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        val historyBody = historyResponse.bodyAsText()
        assertTrue(historyBody.contains("\"activeRunId\":null"))
        assertTrue(historyBody.contains("\"runId\":\"run-interrupted\""))
        assertTrue(historyBody.contains("\"status\":\"FAILED\""))
        assertTrue(historyBody.contains("UI был перезапущен до завершения запуска"))

        val detailsResponse = client.get("/api/module-runs/files/demo-app/runs/run-interrupted")
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        val detailsBody = detailsResponse.bodyAsText()
        assertTrue(detailsBody.contains("\"runId\":\"run-interrupted\""))
        assertTrue(detailsBody.contains("\"status\":\"FAILED\""))
        assertTrue(detailsBody.contains("UI был перезапущен до завершения запуска"))
    }
}

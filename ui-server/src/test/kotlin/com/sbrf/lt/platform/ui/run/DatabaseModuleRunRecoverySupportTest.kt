package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseModuleRunRecoverySupportTest {

    @Test
    fun `database module run service eagerly recovers orphan runs on init`() {
        val runStore = InMemoryDatabaseRunStore(
            mutableMapOf(
                "db-demo" to mutableListOf(
                    sampleRunSummary(runId = "run-orphan", status = "RUNNING"),
                ),
            ),
        )

        val service = DatabaseModuleRunService(
            databaseModuleStore = StubDatabaseModuleRegistryOperations(),
            executionSource = DatabaseModuleExecutionSource(
                connectionProvider = DatabaseConnectionProvider { error("execution source must not be used") },
            ),
            runExecutionStore = runStore,
            runQueryStore = runStore,
            credentialsProvider = StubUiCredentialsProvider(),
        )

        val recovered = service.listRuns("db-demo", limit = 10).runs.single()
        assertEquals("FAILED", recovered.status)
        assertTrue(recovered.errorMessage?.contains("восстановлен как FAILED") == true)
        assertEquals(listOf("run-orphan"), runStore.markedFailedRunIds)
    }

    @Test
    fun `recovery support skips current local active run and fails only stale ones`() {
        val runStore = InMemoryDatabaseRunStore(
            mutableMapOf(
                "db-demo" to mutableListOf(
                    sampleRunSummary(runId = "run-local", status = "RUNNING"),
                    sampleRunSummary(runId = "run-stale", status = "RUNNING"),
                ),
            ),
        )
        val activeRunRegistry = DatabaseModuleActiveRunRegistry().apply {
            markActive("db-demo", "run-local")
        }
        val support = DatabaseModuleRunRecoverySupport(
            runExecutionStore = runStore,
            runQueryStore = runStore,
            activeRunRegistry = activeRunRegistry,
            nowProvider = { Instant.parse("2026-04-24T00:00:00Z") },
        )

        support.recoverModuleOrphanRuns("db-demo")

        assertEquals(listOf("run-stale"), runStore.markedFailedRunIds)
        assertEquals("RUNNING", runStore.listRuns("db-demo", 10).first { it.runId == "run-local" }.status)
        assertEquals("FAILED", runStore.listRuns("db-demo", 10).first { it.runId == "run-stale" }.status)
    }
}

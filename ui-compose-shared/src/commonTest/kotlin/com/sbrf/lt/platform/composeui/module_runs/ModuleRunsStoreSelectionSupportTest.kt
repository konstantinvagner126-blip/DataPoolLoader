package com.sbrf.lt.platform.composeui.module_runs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleRunsStoreSelectionSupportTest {

    @Test
    fun `keeps current selected run when it still exists in history`() {
        val selectedRunId = resolveSelectedRunId(
            history = sampleModuleRunHistory(
                activeRunId = "run-2",
                runIds = listOf("run-1", "run-2", "run-3"),
            ),
            currentSelectedRunId = "run-3",
        )

        assertEquals("run-3", selectedRunId)
    }

    @Test
    fun `falls back to first run when no active run and current selection is missing`() {
        val selectedRunId = resolveSelectedRunId(
            history = sampleModuleRunHistory(
                activeRunId = null,
                runIds = listOf("run-7", "run-8"),
            ),
            currentSelectedRunId = "missing",
        )

        assertEquals("run-7", selectedRunId)
    }

    @Test
    fun `does not call run details API when selected run is null`() {
        val api = StubModuleRunsApi()
        val support = ModuleRunsStoreSelectionSupport(api)

        val details = runModuleRunsSuspend {
            support.loadSelectedRunDetails(
                route = ModuleRunsRouteState(storage = "files", moduleId = "module-a"),
                runId = null,
            )
        }

        assertNull(details)
        assertEquals(0, api.runDetailsLoads)
    }
}

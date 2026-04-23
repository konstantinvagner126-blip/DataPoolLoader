package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRunsStoreLoadingSupportTest {

    @Test
    fun `database load returns fallback state when runtime is not in database mode`() {
        val support = ModuleRunsStoreLoadingSupport(
            StubModuleRunsApi(
                runtimeContextHandler = {
                    sampleModuleRunsRuntimeContext(
                        effectiveMode = ModuleStoreMode.FILES,
                        fallbackReason = "DB mode disabled",
                    )
                },
            ),
        )

        val state = runModuleRunsSuspend {
            support.load(ModuleRunsRouteState(storage = "database", moduleId = "module-a"))
        }

        assertEquals(false, state.loading)
        assertEquals("DB mode disabled", state.errorMessage)
        assertEquals(null, state.history)
        assertEquals(null, state.selectedRunId)
    }

    @Test
    fun `reloadHistory prefers active run when requested`() {
        val support = ModuleRunsStoreLoadingSupport(
            StubModuleRunsApi(
                runtimeContextHandler = {
                    sampleModuleRunsRuntimeContext(effectiveMode = ModuleStoreMode.DATABASE)
                },
                historyHandler = { _, _, _ ->
                    sampleModuleRunHistory(
                        activeRunId = "run-2",
                        runIds = listOf("run-1", "run-2"),
                    )
                },
            ),
        )

        val current = ModuleRunsPageState(
            loading = false,
            runtimeContext = sampleModuleRunsRuntimeContext(effectiveMode = ModuleStoreMode.DATABASE),
            historyLimit = 20,
            selectedRunId = "run-1",
        )

        val state = runModuleRunsSuspend {
            support.reloadHistory(
                current = current,
                route = ModuleRunsRouteState(storage = "database", moduleId = "module-a"),
                preferActiveRun = true,
            )
        }

        assertEquals("run-2", state.selectedRunId)
        assertEquals("run-2", state.selectedRunDetails?.run?.runId)
    }

    @Test
    fun `selectRun does not reload runtime context for files storage`() {
        val api = StubModuleRunsApi()
        val support = ModuleRunsStoreLoadingSupport(api)
        val current = ModuleRunsPageState(
            loading = false,
            runtimeContext = sampleModuleRunsRuntimeContext(),
        )

        val state = runModuleRunsSuspend {
            support.selectRun(
                current = current,
                route = ModuleRunsRouteState(storage = "files", moduleId = "module-a"),
                runId = "run-2",
            )
        }

        assertEquals(0, api.runtimeContextLoads)
        assertEquals(1, api.runDetailsLoads)
        assertEquals("run-2", state.selectedRunId)
        assertEquals("run-2", state.selectedRunDetails?.run?.runId)
    }
}

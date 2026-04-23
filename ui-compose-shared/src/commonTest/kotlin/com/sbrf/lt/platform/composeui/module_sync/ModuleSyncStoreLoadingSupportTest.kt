package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSyncStoreLoadingSupportTest {

    @Test
    fun `non-database runtime keeps normalized file selection and skips run details loading`() {
        val api = StubModuleSyncApi(
            runtimeContextHandler = { sampleModuleSyncRuntimeContext(effectiveMode = ModuleStoreMode.FILES) },
            filesModulesCatalogHandler = { sampleFilesModulesCatalog(listOf("alpha", "gamma")) },
        )
        val support = ModuleSyncStoreLoadingSupport(api)

        val state = runModuleSyncSuspend {
            support.load(
                historyLimit = 25,
                selectiveSyncVisible = true,
                selectedModuleCodes = linkedSetOf("alpha", "missing", "gamma"),
                moduleSearchQuery = "demo",
            )
        }

        assertEquals(false, state.loading)
        assertEquals(ModuleStoreMode.FILES, state.runtimeContext?.effectiveMode)
        assertEquals(linkedSetOf("alpha", "gamma"), state.selectedModuleCodes)
        assertEquals(true, state.selectiveSyncVisible)
        assertEquals("demo", state.moduleSearchQuery)
        assertEquals(emptyList(), state.runs)
        assertEquals(0, api.detailsLoads)
    }

    @Test
    fun `database load prefers active full sync when preferred run is missing`() {
        val support = ModuleSyncStoreLoadingSupport(
            StubModuleSyncApi(
                syncStateHandler = { sampleModuleSyncState(activeFullSyncId = "run-2") },
                runsHandler = {
                    ModuleSyncRunsResponse(sampleModuleSyncRuns(listOf("run-1", "run-2", "run-3")))
                },
            ),
        )

        val state = runModuleSyncSuspend {
            support.load(
                historyLimit = 20,
                preferredRunId = "missing",
                selectiveSyncVisible = false,
                selectedModuleCodes = emptySet(),
                moduleSearchQuery = "",
            )
        }

        assertEquals("run-2", state.selectedRunId)
        assertEquals("run-2", state.selectedRunDetails?.run?.syncRunId)
    }
}

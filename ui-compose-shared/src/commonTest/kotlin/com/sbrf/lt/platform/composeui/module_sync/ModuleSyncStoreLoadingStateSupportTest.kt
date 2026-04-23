package com.sbrf.lt.platform.composeui.module_sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSyncStoreLoadingStateSupportTest {
    private val support = ModuleSyncStoreLoadingStateSupport()

    @Test
    fun `database loaded state keeps selected run and normalized selection`() {
        val runtimeContext = sampleModuleSyncRuntimeContext()
        val syncState = sampleModuleSyncState(activeFullSyncId = "run-2")
        val runs = sampleModuleSyncRuns(listOf("run-1", "run-2"))
        val details = sampleModuleSyncRunDetails("run-2")

        val state = support.createDatabaseLoadedState(
            runtimeContext = runtimeContext,
            syncState = syncState,
            availableFileModules = sampleFilesModulesCatalog(listOf("alpha", "beta")).modules,
            runs = runs,
            selectedRunId = "run-2",
            selectedRunDetails = details,
            historyLimit = 25,
            selectiveSyncVisible = true,
            selectedModuleCodes = linkedSetOf("alpha"),
            moduleSearchQuery = "demo",
            errorMessage = "warning",
        )

        assertEquals(false, state.loading)
        assertEquals(runtimeContext, state.runtimeContext)
        assertEquals(syncState, state.syncState)
        assertEquals(runs, state.runs)
        assertEquals("run-2", state.selectedRunId)
        assertEquals(details, state.selectedRunDetails)
        assertEquals(linkedSetOf("alpha"), state.selectedModuleCodes)
        assertEquals("demo", state.moduleSearchQuery)
        assertEquals("warning", state.errorMessage)
    }

    @Test
    fun `select run failure returns user facing error`() {
        val current = ModuleSyncPageState(loading = true)

        val state = support.applySelectRunFailure(current, IllegalStateException("details failed"))

        assertEquals(false, state.loading)
        assertEquals("details failed", state.errorMessage)
    }
}

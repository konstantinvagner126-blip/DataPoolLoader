package com.sbrf.lt.platform.composeui.module_runs

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRunsStoreStateSupportTest {
    private val support = ModuleRunsStoreStateSupport()

    @Test
    fun `createLoadedState clears loading and keeps selected run payload`() {
        val runtimeContext = sampleModuleRunsRuntimeContext()
        val session = ModuleRunPageSessionResponse(
            storageMode = "files",
            moduleId = "module-a",
            moduleTitle = "Module A",
            moduleMeta = "{}",
        )
        val history = sampleModuleRunHistory(activeRunId = "run-2")
        val details = sampleModuleRunDetails("run-2")

        val state = support.createLoadedState(
            runtimeContext = runtimeContext,
            session = session,
            history = history,
            selectedRunId = "run-2",
            selectedRunDetails = details,
            historyLimit = 25,
        )

        assertEquals(false, state.loading)
        assertEquals(null, state.errorMessage)
        assertEquals(runtimeContext, state.runtimeContext)
        assertEquals(session, state.session)
        assertEquals(history, state.history)
        assertEquals("run-2", state.selectedRunId)
        assertEquals(details, state.selectedRunDetails)
        assertEquals(25, state.historyLimit)
    }

    @Test
    fun `applyReloadedHistory preserves filters while replacing history payload`() {
        val current = ModuleRunsPageState(
            loading = true,
            errorMessage = "stale",
            runtimeContext = sampleModuleRunsRuntimeContext(),
            history = sampleModuleRunHistory(runIds = listOf("run-1")),
            selectedRunId = "run-1",
            selectedRunDetails = sampleModuleRunDetails("run-1"),
            historyLimit = 30,
            historyFilter = ModuleRunsHistoryFilter.FAILED,
            searchQuery = "demo",
        )
        val nextHistory = sampleModuleRunHistory(activeRunId = "run-2", runIds = listOf("run-2", "run-3"))
        val nextDetails = sampleModuleRunDetails("run-2")

        val state = support.applyReloadedHistory(
            current = current,
            runtimeContext = current.runtimeContext,
            history = nextHistory,
            selectedRunId = "run-2",
            selectedRunDetails = nextDetails,
        )

        assertEquals(false, state.loading)
        assertEquals(null, state.errorMessage)
        assertEquals(nextHistory, state.history)
        assertEquals("run-2", state.selectedRunId)
        assertEquals(nextDetails, state.selectedRunDetails)
        assertEquals(ModuleRunsHistoryFilter.FAILED, state.historyFilter)
        assertEquals("demo", state.searchQuery)
    }

    @Test
    fun `applyReloadFailure returns user facing fallback message`() {
        val current = ModuleRunsPageState(loading = true)

        val state = support.applyReloadFailure(current, IllegalStateException("reload failed"))

        assertEquals(false, state.loading)
        assertEquals("reload failed", state.errorMessage)
    }
}

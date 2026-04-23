package com.sbrf.lt.platform.composeui.module_sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleSyncStoreSelectionSupportTest {
    private val support = ModuleSyncStoreSelectionSupport(
        StubModuleSyncApi(),
    )

    @Test
    fun `prefers active single sync when preferred and full sync are absent`() {
        val selectedRunId = support.resolveSelectedRunId(
            runs = sampleModuleSyncRuns(listOf("run-5", "run-6")),
            syncState = sampleModuleSyncState(activeSingleSyncIds = listOf("run-6")),
            preferredRunId = null,
        )

        assertEquals("run-6", selectedRunId)
    }

    @Test
    fun `falls back to first run when there is no preferred or active sync`() {
        val selectedRunId = support.resolveSelectedRunId(
            runs = sampleModuleSyncRuns(listOf("run-9", "run-10")),
            syncState = sampleModuleSyncState(),
            preferredRunId = null,
        )

        assertEquals("run-9", selectedRunId)
    }

    @Test
    fun `does not load details when selected run is absent`() {
        val api = StubModuleSyncApi()
        val support = ModuleSyncStoreSelectionSupport(api)

        val details = runModuleSyncSuspend { support.loadSelectedRunDetails(null).getOrThrow() }

        assertNull(details)
        assertEquals(0, api.detailsLoads)
    }
}

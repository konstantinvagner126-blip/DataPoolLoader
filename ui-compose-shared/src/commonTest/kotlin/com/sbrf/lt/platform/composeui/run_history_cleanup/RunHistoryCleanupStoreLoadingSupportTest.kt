package com.sbrf.lt.platform.composeui.run_history_cleanup

import kotlin.test.Test
import kotlin.test.assertEquals

class RunHistoryCleanupStoreLoadingSupportTest {

    @Test
    fun `load returns runtime unavailable state when runtime context cannot be loaded`() {
        val support = RunHistoryCleanupStoreLoadingSupport(
            StubRunHistoryCleanupApi(
                runtimeContextHandler = { error("runtime down") },
            ),
        )

        val state = runRunHistoryCleanupSuspend {
            support.load(
                disableSafeguard = true,
                outputDisableSafeguard = false,
            )
        }

        assertEquals(false, state.loading)
        assertEquals(true, state.cleanupDisableSafeguard)
        assertEquals(false, state.outputDisableSafeguard)
        assertEquals("runtime down", state.errorMessage)
        assertEquals(null, state.preview)
        assertEquals(null, state.outputPreview)
    }

    @Test
    fun `load aggregates distinct preview errors while keeping runtime context`() {
        val support = RunHistoryCleanupStoreLoadingSupport(
            StubRunHistoryCleanupApi(
                previewHandler = { error("preview failed") },
                outputPreviewHandler = { error("output failed") },
            ),
        )

        val state = runRunHistoryCleanupSuspend { support.load() }

        assertEquals(false, state.loading)
        assertEquals("preview failed\noutput failed", state.errorMessage)
        assertEquals("files", state.runtimeContext?.effectiveMode?.name?.lowercase())
        assertEquals(null, state.preview)
        assertEquals(null, state.outputPreview)
    }
}

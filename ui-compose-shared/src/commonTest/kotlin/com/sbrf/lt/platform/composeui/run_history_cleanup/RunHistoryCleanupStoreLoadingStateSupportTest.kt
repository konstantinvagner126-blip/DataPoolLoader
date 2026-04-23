package com.sbrf.lt.platform.composeui.run_history_cleanup

import kotlin.test.Test
import kotlin.test.assertEquals

class RunHistoryCleanupStoreLoadingStateSupportTest {
    private val support = RunHistoryCleanupStoreLoadingStateSupport()

    @Test
    fun `loaded state keeps previews and aggregates distinct error messages`() {
        val state = support.createLoadedState(
            runtimeContext = sampleRunHistoryCleanupRuntimeContext(),
            disableSafeguard = true,
            outputDisableSafeguard = false,
            preview = sampleRunHistoryCleanupPreview(),
            outputPreview = sampleOutputRetentionPreview(),
            previewErrorMessage = "preview failed",
            outputPreviewErrorMessage = "output failed",
        )

        assertEquals(false, state.loading)
        assertEquals(true, state.cleanupDisableSafeguard)
        assertEquals(false, state.outputDisableSafeguard)
        assertEquals("preview failed\noutput failed", state.errorMessage)
        assertEquals("files", state.preview?.storageMode)
        assertEquals("files", state.outputPreview?.storageMode)
    }

    @Test
    fun `runtime unavailable state keeps safeguard toggles`() {
        val state = support.createRuntimeUnavailableState(
            disableSafeguard = true,
            outputDisableSafeguard = true,
            errorMessage = "runtime unavailable",
        )

        assertEquals(false, state.loading)
        assertEquals(true, state.cleanupDisableSafeguard)
        assertEquals(true, state.outputDisableSafeguard)
        assertEquals("runtime unavailable", state.errorMessage)
        assertEquals(null, state.runtimeContext)
    }
}

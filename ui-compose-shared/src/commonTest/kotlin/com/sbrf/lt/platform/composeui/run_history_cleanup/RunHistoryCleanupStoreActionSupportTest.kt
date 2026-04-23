package com.sbrf.lt.platform.composeui.run_history_cleanup

import kotlin.test.Test
import kotlin.test.assertEquals

class RunHistoryCleanupStoreActionSupportTest {

    @Test
    fun `refreshPreview clears action state and updates preview`() {
        val support = RunHistoryCleanupStoreActionSupport(
            api = StubRunHistoryCleanupApi(
                previewHandler = { disableSafeguard ->
                    sampleRunHistoryCleanupPreview().copy(safeguardEnabled = !disableSafeguard)
                },
            ),
            loadingSupport = RunHistoryCleanupStoreLoadingSupport(StubRunHistoryCleanupApi()),
        )

        val state = runRunHistoryCleanupSuspend {
            support.refreshPreview(
                RunHistoryCleanupPageState(
                    loading = true,
                    actionInProgress = "preview",
                    cleanupDisableSafeguard = true,
                    errorMessage = "old",
                ),
            )
        }

        assertEquals(false, state.loading)
        assertEquals(null, state.actionInProgress)
        assertEquals(null, state.errorMessage)
        assertEquals(false, state.preview?.safeguardEnabled)
    }

    @Test
    fun `cleanupOutputs refreshes state and sets success message`() {
        val api = StubRunHistoryCleanupApi(
            cleanupOutputsHandler = {
                sampleOutputRetentionResult(totalOutputDirsDeleted = 3)
            },
            outputPreviewHandler = {
                sampleOutputRetentionPreview().copy(totalOutputDirsToDelete = 0)
            },
            previewHandler = {
                sampleRunHistoryCleanupPreview()
            },
        )
        val support = RunHistoryCleanupStoreActionSupport(
            api = api,
            loadingSupport = RunHistoryCleanupStoreLoadingSupport(api),
        )

        val state = runRunHistoryCleanupSuspend {
            support.cleanupOutputs(
                RunHistoryCleanupPageState(
                    loading = false,
                    actionInProgress = "cleanupOutputs",
                    outputDisableSafeguard = true,
                ),
            )
        }

        assertEquals(null, state.actionInProgress)
        assertEquals("Очистка output завершена: удалено 3 каталогов.", state.successMessage)
        assertEquals(null, state.errorMessage)
        assertEquals(0, state.outputPreview?.totalOutputDirsToDelete)
    }
}

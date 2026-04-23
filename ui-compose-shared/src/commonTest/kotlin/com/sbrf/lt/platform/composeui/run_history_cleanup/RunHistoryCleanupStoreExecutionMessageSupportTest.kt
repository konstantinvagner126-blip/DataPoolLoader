package com.sbrf.lt.platform.composeui.run_history_cleanup

import kotlin.test.Test
import kotlin.test.assertEquals

class RunHistoryCleanupStoreExecutionMessageSupportTest {
    private val support = RunHistoryCleanupStoreExecutionMessageSupport()

    @Test
    fun `run history success message reports deleted runs when cleanup removed data`() {
        assertEquals(
            "Очистка завершена: удалено 5 запусков.",
            support.runHistorySuccessMessage(
                sampleRunHistoryCleanupResult(totalRunsDeleted = 5),
            ),
        )
    }

    @Test
    fun `run history success message reports no-op cleanup`() {
        assertEquals(
            "Очистка завершена: удалять было нечего.",
            support.runHistorySuccessMessage(sampleRunHistoryCleanupResult()),
        )
    }

    @Test
    fun `output success message treats missing output dirs as meaningful cleanup`() {
        assertEquals(
            "Очистка output завершена: удалено 0 каталогов.",
            support.outputSuccessMessage(
                sampleOutputRetentionResult(totalMissingOutputDirs = 2),
            ),
        )
    }
}

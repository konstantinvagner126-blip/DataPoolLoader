package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreExecutionMessageSupport {
    fun runHistorySuccessMessage(result: RunHistoryCleanupResultResponse): String =
        if (result.totalRunsDeleted > 0 || result.totalOrphanExecutionSnapshotsDeleted > 0) {
            "Очистка завершена: удалено ${result.totalRunsDeleted} запусков."
        } else {
            "Очистка завершена: удалять было нечего."
        }

    fun outputSuccessMessage(result: OutputRetentionResultResponse): String =
        if (result.totalOutputDirsDeleted > 0 || result.totalMissingOutputDirs > 0) {
            "Очистка output завершена: удалено ${result.totalOutputDirsDeleted} каталогов."
        } else {
            "Очистка output завершена: удалять было нечего."
        }
}

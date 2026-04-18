package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.model.RuntimeContext

interface RunHistoryCleanupApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadPreview(disableSafeguard: Boolean): RunHistoryCleanupPreviewResponse

    suspend fun cleanupRunHistory(disableSafeguard: Boolean): RunHistoryCleanupResultResponse

    suspend fun loadOutputPreview(disableSafeguard: Boolean): OutputRetentionPreviewResponse

    suspend fun cleanupOutputs(disableSafeguard: Boolean): OutputRetentionResultResponse
}

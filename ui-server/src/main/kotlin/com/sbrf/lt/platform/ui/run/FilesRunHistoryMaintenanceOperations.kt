package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant

interface FilesRunHistoryMaintenanceOperations {
    fun previewHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse

    fun executeHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupResultResponse
}

package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Controlled cleanup истории FILES-запусков, сохраненной в persisted UI state.
 */
class FilesRunHistoryCleanupService(
    private val runManager: RunManager,
    private val retentionDays: Int = 30,
    private val keepMinRunsPerModule: Int = 30,
) {
    fun previewCleanup(disableSafeguard: Boolean = false): RunHistoryCleanupPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        return runManager.previewHistoryCleanup(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
    }

    fun executeCleanup(disableSafeguard: Boolean = false): RunHistoryCleanupResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        return runManager.executeHistoryCleanup(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
    }

    private fun cleanupCutoff(): Instant =
        Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
}

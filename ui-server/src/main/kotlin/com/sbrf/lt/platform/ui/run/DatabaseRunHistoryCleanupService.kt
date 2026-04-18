package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Сервис controlled cleanup истории DB-запусков.
 */
open class DatabaseRunHistoryCleanupService(
    private val runStore: DatabaseRunStore,
    private val retentionDays: Int = 30,
    private val keepMinRunsPerModule: Int = 30,
) {
    open fun previewCleanup(disableSafeguard: Boolean = false): DatabaseRunHistoryCleanupPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        return runStore.previewCleanup(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
    }

    open fun executeCleanup(disableSafeguard: Boolean = false): DatabaseRunHistoryCleanupResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        return runStore.executeCleanup(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
    }

    private fun cleanupCutoff(): Instant =
        Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
}

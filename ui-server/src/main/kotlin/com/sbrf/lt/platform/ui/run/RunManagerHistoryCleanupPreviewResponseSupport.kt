package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import java.time.Instant

internal class RunManagerHistoryCleanupPreviewResponseSupport(
    private val planningSupport: RunManagerHistoryCleanupPlanningSupport,
    private val historyUsageSupport: RunManagerHistoryUsageSupport,
) {
    fun buildPreviewResponse(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse {
        val preview = planningSupport.buildHistoryCleanupPreview(
            snapshots = snapshots,
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
        return RunHistoryCleanupPreviewResponse(
            storageMode = "FILES",
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentRunsCount = snapshots.size,
            currentModulesCount = snapshots.map { it.moduleId }.distinct().size,
            currentStorageBytes = planningSupport.currentFileSizeBytes(),
            currentOldestRequestedAt = snapshots.minOfOrNull { it.startedAt },
            currentNewestRequestedAt = snapshots.maxOfOrNull { it.startedAt },
            currentTopModules = historyUsageSupport.buildCurrentHistoryUsageModules(snapshots),
            estimatedBytesToFree = planningSupport.estimateHistoryCleanupBytesToFree(snapshots, preview.runIds),
            totalModulesAffected = preview.modules.size,
            totalRunsToDelete = preview.runIds.size,
            totalEventsToDelete = preview.totalEventsToDelete,
            modules = preview.modules,
        )
    }
}

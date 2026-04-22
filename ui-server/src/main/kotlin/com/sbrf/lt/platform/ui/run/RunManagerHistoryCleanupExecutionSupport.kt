package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant

internal class RunManagerHistoryCleanupExecutionSupport(
    private val planningSupport: RunManagerHistoryCleanupPlanningSupport,
) {
    fun executeHistoryCleanup(
        snapshots: MutableList<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): HistoryCleanupExecutionResult {
        val preview = planningSupport.buildHistoryCleanupPreview(
            snapshots = snapshots,
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
        val deleted = if (preview.runIds.isNotEmpty()) {
            snapshots.removeAll { snapshot -> snapshot.id in preview.runIds }
        } else {
            false
        }
        return HistoryCleanupExecutionResult(
            deleted = deleted,
            response = RunHistoryCleanupResultResponse(
                storageMode = "FILES",
                safeguardEnabled = !disableSafeguard,
                retentionDays = retentionDays,
                keepMinRunsPerModule = keepMinRunsPerModule,
                cutoffTimestamp = cutoffTimestamp,
                finishedAt = Instant.now(),
                totalModulesAffected = preview.modules.size,
                totalRunsDeleted = preview.runIds.size,
                totalEventsDeleted = preview.totalEventsToDelete,
                modules = preview.modules,
            ),
        )
    }
}

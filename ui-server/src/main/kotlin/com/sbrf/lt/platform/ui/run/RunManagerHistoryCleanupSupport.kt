package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant

internal class RunManagerHistoryCleanupSupport(
    private val stateStore: RunStateStore,
    private val objectMapper: ObjectMapper,
) {
    private val cleanupPlanningSupport = RunManagerHistoryCleanupPlanningSupport(
        stateStore = stateStore,
        objectMapper = objectMapper,
    )
    private val historyUsageSupport = RunManagerHistoryUsageSupport(objectMapper)

    fun previewHistoryCleanup(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse {
        val preview = cleanupPlanningSupport.buildHistoryCleanupPreview(
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
            currentStorageBytes = cleanupPlanningSupport.currentFileSizeBytes(),
            currentOldestRequestedAt = snapshots.minOfOrNull { it.startedAt },
            currentNewestRequestedAt = snapshots.maxOfOrNull { it.startedAt },
            currentTopModules = historyUsageSupport.buildCurrentHistoryUsageModules(snapshots),
            estimatedBytesToFree = cleanupPlanningSupport.estimateHistoryCleanupBytesToFree(snapshots, preview.runIds),
            totalModulesAffected = preview.modules.size,
            totalRunsToDelete = preview.runIds.size,
            totalEventsToDelete = preview.totalEventsToDelete,
            modules = preview.modules,
        )
    }

    fun executeHistoryCleanup(
        snapshots: MutableList<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): HistoryCleanupExecutionResult {
        val preview = cleanupPlanningSupport.buildHistoryCleanupPreview(
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

internal data class HistoryCleanupExecutionResult(
    val deleted: Boolean,
    val response: RunHistoryCleanupResultResponse,
)

package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant

internal class RunManagerHistoryCleanupSupport(
    private val stateStore: RunStateStore,
    private val objectMapper: ObjectMapper,
) {
    fun previewHistoryCleanup(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse {
        val preview = buildHistoryCleanupPreview(
            snapshots = snapshots,
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
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
            currentStorageBytes = stateStore.currentFileSizeBytes(),
            currentOldestRequestedAt = snapshots.minOfOrNull { it.startedAt },
            currentNewestRequestedAt = snapshots.maxOfOrNull { it.startedAt },
            currentTopModules = buildCurrentHistoryUsageModules(snapshots),
            estimatedBytesToFree = estimateHistoryCleanupBytesToFree(snapshots, preview.runIds),
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
        val preview = buildHistoryCleanupPreview(
            snapshots = snapshots,
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
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

    private fun buildHistoryCleanupPreview(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): FilesHistoryCleanupPreview {
        val deletableByModule = snapshots
            .sortedByDescending { it.startedAt }
            .groupBy { it.moduleId }
            .mapNotNull { (moduleId, moduleSnapshots) ->
                val runsToDelete = moduleSnapshots.filterIndexed { index, snapshot ->
                    val olderThanCutoff = snapshot.startedAt.isBefore(cutoffTimestamp)
                    val canDeleteBySafeguard = disableSafeguard || index >= keepMinRunsPerModule
                    snapshot.status != ExecutionStatus.RUNNING &&
                        olderThanCutoff &&
                        canDeleteBySafeguard
                }
                if (runsToDelete.isEmpty()) {
                    null
                } else {
                    FilesHistoryCleanupModulePreview(
                        moduleCode = moduleId,
                        runIds = runsToDelete.map { it.id },
                        totalEventsToDelete = runsToDelete.sumOf { it.events.size },
                        summary = RunHistoryCleanupModuleResponse(
                            moduleCode = moduleId,
                            totalRunsToDelete = runsToDelete.size,
                            oldestRequestedAt = runsToDelete.minOfOrNull { it.startedAt },
                            newestRequestedAt = runsToDelete.maxOfOrNull { it.startedAt },
                        ),
                    )
                }
            }

        return FilesHistoryCleanupPreview(
            runIds = deletableByModule.flatMapTo(linkedSetOf()) { it.runIds },
            totalEventsToDelete = deletableByModule.sumOf { it.totalEventsToDelete },
            modules = deletableByModule.map { it.summary }.sortedBy { it.moduleCode },
        )
    }

    private fun estimateHistoryCleanupBytesToFree(
        snapshots: List<MutableRunSnapshot>,
        runIdsToDelete: Set<String>,
    ): Long? {
        val currentSize = stateStore.currentFileSizeBytes()
        if (currentSize <= 0L || runIdsToDelete.isEmpty()) {
            return 0L
        }
        val projectedBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
            PersistedRunState(
                history = snapshots
                    .filterNot { it.id in runIdsToDelete }
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        ).size.toLong()
        return (currentSize - projectedBytes).coerceAtLeast(0L)
    }

    private fun buildCurrentHistoryUsageModules(
        snapshots: List<MutableRunSnapshot>,
    ): List<CurrentStorageModuleResponse> =
        snapshots
            .groupBy { it.moduleId }
            .map { (moduleCode, moduleSnapshots) ->
                CurrentStorageModuleResponse(
                    moduleCode = moduleCode,
                    currentRunsCount = moduleSnapshots.size,
                    currentStorageBytes = estimateHistoryStorageBytesForSnapshots(moduleSnapshots),
                    oldestRequestedAt = moduleSnapshots.minOfOrNull { it.startedAt },
                    newestRequestedAt = moduleSnapshots.maxOfOrNull { it.startedAt },
                )
            }
            .sortedWith(
                compareByDescending<CurrentStorageModuleResponse> { it.currentStorageBytes }
                    .thenByDescending { it.currentRunsCount }
                    .thenBy { it.moduleCode },
            )
            .take(5)

    private fun estimateHistoryStorageBytesForSnapshots(moduleSnapshots: List<MutableRunSnapshot>): Long =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
            PersistedRunState(
                history = moduleSnapshots
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        ).size.toLong()
}

internal data class HistoryCleanupExecutionResult(
    val deleted: Boolean,
    val response: RunHistoryCleanupResultResponse,
)

private data class FilesHistoryCleanupPreview(
    val runIds: Set<String>,
    val totalEventsToDelete: Int,
    val modules: List<RunHistoryCleanupModuleResponse>,
)

private data class FilesHistoryCleanupModulePreview(
    val moduleCode: String,
    val runIds: List<String>,
    val totalEventsToDelete: Int,
    val summary: RunHistoryCleanupModuleResponse,
)

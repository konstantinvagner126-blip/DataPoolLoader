package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse
import java.time.Instant

internal class RunManagerHistoryCleanupPlanningSupport(
    private val stateStore: RunStateStore,
    private val objectMapper: ObjectMapper,
) {
    fun buildHistoryCleanupPreview(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
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

    fun estimateHistoryCleanupBytesToFree(
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

    fun currentFileSizeBytes(): Long = stateStore.currentFileSizeBytes()
}

internal data class FilesHistoryCleanupPreview(
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

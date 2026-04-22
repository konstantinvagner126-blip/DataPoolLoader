package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import java.time.Instant

internal class FilesOutputRetentionPlanningSupport(
    private val runManager: FilesModuleRunOperations,
    private val keepMinRunsPerModule: Int,
) {
    fun buildCleanupPlan(
        cutoffTimestamp: Instant,
        disableSafeguard: Boolean,
    ): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(
            runManager.currentState().history
                .sortedByDescending { it.startedAt }
                .groupBy { it.moduleId }
                .flatMap { (moduleId, runs) ->
                    runs.filterIndexed { index, run ->
                        val outputDir = run.outputDir?.trim().orEmpty()
                        val olderThanCutoff = run.startedAt.isBefore(cutoffTimestamp)
                        val canDeleteBySafeguard = disableSafeguard || index >= keepMinRunsPerModule
                        run.status != ExecutionStatus.RUNNING &&
                            outputDir.isNotEmpty() &&
                            olderThanCutoff &&
                            canDeleteBySafeguard
                    }.map { run ->
                        OutputRetentionRunRef(
                            moduleCode = moduleId,
                            requestedAt = run.startedAt,
                            outputDir = run.outputDir.orEmpty(),
                        )
                    }
                },
        )

    fun buildCurrentUsagePlan(): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(
            runManager.currentState().history
                .filter { !it.outputDir.isNullOrBlank() }
                .map { run ->
                    OutputRetentionRunRef(
                        moduleCode = run.moduleId,
                        requestedAt = run.startedAt,
                        outputDir = run.outputDir.orEmpty(),
                    )
                },
        )
}

package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

class FilesOutputRetentionService(
    private val runManager: FilesModuleRunOperations,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        val currentUsage = buildCurrentUsagePlan()
        val cleanupPlan = buildPlan(cutoffTimestamp, disableSafeguard)
        return OutputRetentionResponseSupport.buildPreviewResponse(
            storageMode = "FILES",
            disableSafeguard = disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentUsage = currentUsage,
            cleanupPlan = cleanupPlan,
        )
    }

    fun executeCleanup(disableSafeguard: Boolean = false): OutputRetentionResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        val cleanupPlan = buildPlan(cutoffTimestamp, disableSafeguard)
        val deleteResult = OutputRetentionPlanner.delete(cleanupPlan)
        return OutputRetentionResponseSupport.buildResultResponse(
            storageMode = "FILES",
            disableSafeguard = disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = Instant.now(),
            cleanupPlan = cleanupPlan,
            deleteResult = deleteResult,
        )
    }

    private fun buildPlan(
        cutoffTimestamp: Instant,
        disableSafeguard: Boolean,
    ): OutputRetentionPlan {
        val candidates = runManager.currentState().history
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
            }
        return OutputRetentionPlanner.buildPlan(candidates)
    }

    private fun buildCurrentUsagePlan(): OutputRetentionPlan =
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

    private fun cleanupCutoff(): Instant =
        Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
}

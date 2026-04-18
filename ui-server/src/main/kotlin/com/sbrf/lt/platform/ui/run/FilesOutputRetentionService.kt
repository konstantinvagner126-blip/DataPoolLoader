package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

class FilesOutputRetentionService(
    private val runManager: RunManager,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        val currentUsage = buildCurrentUsagePlan()
        val plan = buildPlan(cutoffTimestamp, disableSafeguard)
        return OutputRetentionPreviewResponse(
            storageMode = "FILES",
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentRunsWithOutput = currentUsage.totalRunsAffected,
            currentModulesWithOutput = currentUsage.modules.size,
            currentOutputDirs = currentUsage.directories.size,
            currentBytes = currentUsage.totalBytesToFree,
            currentOldestRequestedAt = currentUsage.runs.minOfOrNull { it.requestedAt },
            currentNewestRequestedAt = currentUsage.runs.maxOfOrNull { it.requestedAt },
            currentTopModules = currentUsage.modules
                .sortedWith(
                    compareByDescending<OutputRetentionModuleResponse> { it.totalBytesToFree }
                        .thenByDescending { it.totalOutputDirsToDelete }
                        .thenBy { it.moduleCode },
                )
                .take(5)
                .map { module ->
                    CurrentStorageModuleResponse(
                        moduleCode = module.moduleCode,
                        currentRunsCount = module.totalRunsAffected,
                        currentStorageBytes = module.totalBytesToFree,
                        currentOutputDirs = module.totalOutputDirsToDelete,
                        oldestRequestedAt = module.oldestRequestedAt,
                        newestRequestedAt = module.newestRequestedAt,
                    )
                },
            totalModulesAffected = plan.modules.size,
            totalRunsAffected = plan.totalRunsAffected,
            totalOutputDirsToDelete = plan.totalOutputDirsToDelete,
            totalMissingOutputDirs = plan.totalMissingOutputDirs,
            totalBytesToFree = plan.totalBytesToFree,
            modules = plan.modules,
        )
    }

    fun executeCleanup(disableSafeguard: Boolean = false): OutputRetentionResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        val plan = buildPlan(cutoffTimestamp, disableSafeguard)
        val deleteResult = OutputRetentionPlanner.delete(plan)
        return OutputRetentionResultResponse(
            storageMode = "FILES",
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = Instant.now(),
            totalModulesAffected = plan.modules.size,
            totalRunsAffected = plan.totalRunsAffected,
            totalOutputDirsDeleted = deleteResult.deletedDirs,
            totalMissingOutputDirs = deleteResult.missingDirs,
            totalBytesFreed = deleteResult.bytesFreed,
            modules = plan.modules,
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

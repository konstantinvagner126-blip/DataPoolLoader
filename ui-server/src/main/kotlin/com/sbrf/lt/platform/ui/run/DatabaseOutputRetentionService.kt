package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

open class DatabaseOutputRetentionService(
    private val runStore: DatabaseRunStore,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    open fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        val currentUsage = buildCurrentUsagePlan()
        val plan = buildPlan(cutoffTimestamp, disableSafeguard)
        return OutputRetentionPreviewResponse(
            storageMode = "DATABASE",
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

    open fun executeCleanup(disableSafeguard: Boolean = false): OutputRetentionResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        val plan = buildPlan(cutoffTimestamp, disableSafeguard)
        val deleteResult = OutputRetentionPlanner.delete(plan)
        return OutputRetentionResultResponse(
            storageMode = "DATABASE",
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
    ): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(
            runStore.listOutputRetentionCandidates(
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            ),
        )

    private fun buildCurrentUsagePlan(): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(runStore.listCurrentOutputUsageCandidates())

    private fun cleanupCutoff(): Instant =
        Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
}

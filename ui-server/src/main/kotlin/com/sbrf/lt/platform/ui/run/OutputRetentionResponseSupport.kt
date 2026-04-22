package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant

internal object OutputRetentionResponseSupport {
    fun buildPreviewResponse(
        storageMode: String,
        disableSafeguard: Boolean,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        cutoffTimestamp: Instant,
        currentUsage: OutputRetentionPlan,
        cleanupPlan: OutputRetentionPlan,
    ): OutputRetentionPreviewResponse =
        OutputRetentionPreviewResponse(
            storageMode = storageMode,
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
            currentTopModules = currentUsage.topModules(),
            totalModulesAffected = cleanupPlan.modules.size,
            totalRunsAffected = cleanupPlan.totalRunsAffected,
            totalOutputDirsToDelete = cleanupPlan.totalOutputDirsToDelete,
            totalMissingOutputDirs = cleanupPlan.totalMissingOutputDirs,
            totalBytesToFree = cleanupPlan.totalBytesToFree,
            modules = cleanupPlan.modules,
        )

    fun buildResultResponse(
        storageMode: String,
        disableSafeguard: Boolean,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        cutoffTimestamp: Instant,
        finishedAt: Instant,
        cleanupPlan: OutputRetentionPlan,
        deleteResult: OutputRetentionDeleteResult,
    ): OutputRetentionResultResponse =
        OutputRetentionResultResponse(
            storageMode = storageMode,
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = finishedAt,
            totalModulesAffected = cleanupPlan.modules.size,
            totalRunsAffected = cleanupPlan.totalRunsAffected,
            totalOutputDirsDeleted = deleteResult.deletedDirs,
            totalMissingOutputDirs = deleteResult.missingDirs,
            totalBytesFreed = deleteResult.bytesFreed,
            modules = cleanupPlan.modules,
        )

    private fun OutputRetentionPlan.topModules(): List<CurrentStorageModuleResponse> =
        modules
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
            }
}

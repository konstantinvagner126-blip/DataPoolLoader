package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse

internal fun DatabaseRunHistoryCleanupPreviewResponse.toCommonDatabaseRunHistoryCleanupPreviewResponse(): RunHistoryCleanupPreviewResponse =
    RunHistoryCleanupPreviewResponse(
        storageMode = "DATABASE",
        safeguardEnabled = safeguardEnabled,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        cutoffTimestamp = cutoffTimestamp,
        currentRunsCount = currentRunsCount,
        currentModulesCount = currentModulesCount,
        currentStorageBytes = currentStorageBytes,
        currentOldestRequestedAt = currentOldestRequestedAt,
        currentNewestRequestedAt = currentNewestRequestedAt,
        currentTopModules = currentTopModules.map { module ->
            CurrentStorageModuleResponse(
                moduleCode = module.moduleCode,
                currentRunsCount = module.currentRunsCount,
                currentStorageBytes = module.currentStorageBytes,
                currentOutputDirs = module.currentOutputDirs,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
        estimatedBytesToFree = estimatedBytesToFree,
        totalModulesAffected = totalModulesAffected,
        totalRunsToDelete = totalRunsToDelete,
        totalSourceResultsToDelete = totalSourceResultsToDelete,
        totalEventsToDelete = totalEventsToDelete,
        totalArtifactsToDelete = totalArtifactsToDelete,
        totalOrphanExecutionSnapshotsToDelete = totalOrphanExecutionSnapshotsToDelete,
        modules = modules.map { module ->
            RunHistoryCleanupModuleResponse(
                moduleCode = module.moduleCode,
                totalRunsToDelete = module.totalRunsToDelete,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
    )

internal fun DatabaseRunHistoryCleanupResultResponse.toCommonDatabaseRunHistoryCleanupResultResponse(): RunHistoryCleanupResultResponse =
    RunHistoryCleanupResultResponse(
        storageMode = "DATABASE",
        safeguardEnabled = safeguardEnabled,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        cutoffTimestamp = cutoffTimestamp,
        finishedAt = finishedAt,
        totalModulesAffected = totalModulesAffected,
        totalRunsDeleted = totalRunsDeleted,
        totalSourceResultsDeleted = totalSourceResultsDeleted,
        totalEventsDeleted = totalEventsDeleted,
        totalArtifactsDeleted = totalArtifactsDeleted,
        totalOrphanExecutionSnapshotsDeleted = totalOrphanExecutionSnapshotsDeleted,
        modules = modules.map { module ->
            RunHistoryCleanupModuleResponse(
                moduleCode = module.moduleCode,
                totalRunsToDelete = module.totalRunsToDelete,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
    )

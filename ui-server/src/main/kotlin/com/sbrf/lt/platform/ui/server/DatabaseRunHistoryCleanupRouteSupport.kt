package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
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
        currentTopModules = toCommonCurrentStorageModules(),
        estimatedBytesToFree = estimatedBytesToFree,
        totalModulesAffected = totalModulesAffected,
        totalRunsToDelete = totalRunsToDelete,
        totalSourceResultsToDelete = totalSourceResultsToDelete,
        totalEventsToDelete = totalEventsToDelete,
        totalArtifactsToDelete = totalArtifactsToDelete,
        totalOrphanExecutionSnapshotsToDelete = totalOrphanExecutionSnapshotsToDelete,
        modules = toCommonCleanupModules(),
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
        modules = toCommonCleanupModules(),
    )

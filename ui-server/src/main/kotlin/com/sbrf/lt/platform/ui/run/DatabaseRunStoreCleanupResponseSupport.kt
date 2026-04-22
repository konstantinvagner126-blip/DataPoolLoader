package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.time.Instant

internal class DatabaseRunStoreCleanupResponseSupport {
    fun buildPreviewResponse(
        preview: DatabaseRunHistoryCleanupPreviewData,
        currentUsage: DatabaseHistoryStorageUsage,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewResponse =
        DatabaseRunHistoryCleanupPreviewResponse(
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentRunsCount = currentUsage.totalRuns,
            currentModulesCount = currentUsage.totalModules,
            currentStorageBytes = currentUsage.totalStorageBytes,
            currentOldestRequestedAt = currentUsage.oldestRequestedAt,
            currentNewestRequestedAt = currentUsage.newestRequestedAt,
            currentTopModules = currentUsage.topModules,
            totalModulesAffected = preview.modules.size,
            totalRunsToDelete = preview.totalRunsToDelete,
            totalSourceResultsToDelete = preview.totalSourceResultsToDelete,
            totalEventsToDelete = preview.totalEventsToDelete,
            totalArtifactsToDelete = preview.totalArtifactsToDelete,
            totalOrphanExecutionSnapshotsToDelete = preview.totalOrphanExecutionSnapshotsToDelete,
            modules = preview.modules,
        )

    fun buildResultResponse(
        preview: DatabaseRunHistoryCleanupPreviewData,
        deletedOrphanSnapshots: Int,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupResultResponse =
        DatabaseRunHistoryCleanupResultResponse(
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = Instant.now(),
            totalModulesAffected = preview.modules.size,
            totalRunsDeleted = preview.totalRunsToDelete,
            totalSourceResultsDeleted = preview.totalSourceResultsToDelete,
            totalEventsDeleted = preview.totalEventsToDelete,
            totalArtifactsDeleted = preview.totalArtifactsToDelete,
            totalOrphanExecutionSnapshotsDeleted = deletedOrphanSnapshots,
            modules = preview.modules,
        )
}

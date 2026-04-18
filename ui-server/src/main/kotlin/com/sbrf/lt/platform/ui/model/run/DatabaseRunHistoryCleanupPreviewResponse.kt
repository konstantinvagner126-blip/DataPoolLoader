package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Dry-run preview cleanup истории DB-запусков.
 */
data class DatabaseRunHistoryCleanupPreviewResponse(
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: Instant,
    val totalModulesAffected: Int = 0,
    val totalRunsToDelete: Int = 0,
    val totalSourceResultsToDelete: Int = 0,
    val totalEventsToDelete: Int = 0,
    val totalArtifactsToDelete: Int = 0,
    val totalOrphanExecutionSnapshotsToDelete: Int = 0,
    val modules: List<DatabaseRunHistoryCleanupModuleResponse> = emptyList(),
)

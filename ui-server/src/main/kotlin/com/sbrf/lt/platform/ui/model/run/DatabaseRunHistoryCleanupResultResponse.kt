package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Результат фактического cleanup истории DB-запусков.
 */
data class DatabaseRunHistoryCleanupResultResponse(
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: Instant,
    val finishedAt: Instant,
    val totalModulesAffected: Int = 0,
    val totalRunsDeleted: Int = 0,
    val totalSourceResultsDeleted: Int = 0,
    val totalEventsDeleted: Int = 0,
    val totalArtifactsDeleted: Int = 0,
    val totalOrphanExecutionSnapshotsDeleted: Int = 0,
    val modules: List<DatabaseRunHistoryCleanupModuleResponse> = emptyList(),
)

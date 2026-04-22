package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupPreviewSupport(
    private val normalizedSchema: String,
    private val statementSupport: DatabaseRunStoreCleanupStatementSupport,
) {
    private val modulePreviewSupport = DatabaseRunStoreCleanupModulePreviewSupport(normalizedSchema, statementSupport)
    private val countSupport = DatabaseRunStoreCleanupCountSupport(statementSupport)

    fun loadCleanupPreview(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewData {
        val modules = modulePreviewSupport.loadModules(
            connection = connection,
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )

        return DatabaseRunHistoryCleanupPreviewData(
            modules = modules,
            totalRunsToDelete = countSupport.queryCleanupCount(
                connection, RunHistorySql.countCleanupRuns(normalizedSchema), "total_runs_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalSourceResultsToDelete = countSupport.queryCleanupCount(
                connection, RunHistorySql.countCleanupSourceResults(normalizedSchema), "total_source_results_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalEventsToDelete = countSupport.queryCleanupCount(
                connection, RunHistorySql.countCleanupEvents(normalizedSchema), "total_events_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalArtifactsToDelete = countSupport.queryCleanupCount(
                connection, RunHistorySql.countCleanupArtifacts(normalizedSchema), "total_artifacts_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalOrphanExecutionSnapshotsToDelete = countSupport.queryCleanupCount(
                connection,
                RunHistorySql.countCleanupOrphanExecutionSnapshots(normalizedSchema),
                "total_orphan_execution_snapshots_to_delete",
                cutoffTimestamp,
                keepMinRunsPerModule,
                disableSafeguard,
                includeSnapshotCutoff = true,
            ),
        )
    }
}

package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupPreviewSupport(
    private val normalizedSchema: String,
    private val statementSupport: DatabaseRunStoreCleanupStatementSupport,
) {
    fun loadCleanupPreview(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewData {
        val modules = statementSupport.prepareCleanupStatement(
            connection = connection,
            sql = RunHistorySql.listCleanupModules(normalizedSchema),
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
            includeSnapshotCutoff = false,
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<DatabaseRunHistoryCleanupModuleResponse>()
                while (rs.next()) {
                    result += DatabaseRunHistoryCleanupModuleResponse(
                        moduleCode = rs.getString("module_code"),
                        totalRunsToDelete = rs.getInt("total_runs_to_delete"),
                        oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                        newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                    )
                }
                result
            }
        }

        return DatabaseRunHistoryCleanupPreviewData(
            modules = modules,
            totalRunsToDelete = queryCleanupCount(
                connection, RunHistorySql.countCleanupRuns(normalizedSchema), "total_runs_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalSourceResultsToDelete = queryCleanupCount(
                connection, RunHistorySql.countCleanupSourceResults(normalizedSchema), "total_source_results_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalEventsToDelete = queryCleanupCount(
                connection, RunHistorySql.countCleanupEvents(normalizedSchema), "total_events_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalArtifactsToDelete = queryCleanupCount(
                connection, RunHistorySql.countCleanupArtifacts(normalizedSchema), "total_artifacts_to_delete",
                cutoffTimestamp, keepMinRunsPerModule, disableSafeguard, includeSnapshotCutoff = false,
            ),
            totalOrphanExecutionSnapshotsToDelete = queryCleanupCount(
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

    private fun queryCleanupCount(
        connection: Connection,
        sql: String,
        columnName: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
        includeSnapshotCutoff: Boolean,
    ): Int =
        statementSupport.prepareCleanupStatement(
            connection = connection,
            sql = sql,
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
            includeSnapshotCutoff = includeSnapshotCutoff,
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0 else rs.getInt(columnName)
            }
        }
}

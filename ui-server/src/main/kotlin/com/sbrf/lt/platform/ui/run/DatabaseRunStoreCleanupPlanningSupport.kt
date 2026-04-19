package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreCleanupPlanningSupport(
    private val normalizedSchema: String,
) {
    fun loadCleanupPreview(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewData {
        val modules = prepareCleanupStatement(
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
                connection = connection,
                sql = RunHistorySql.countCleanupRuns(normalizedSchema),
                columnName = "total_runs_to_delete",
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
                includeSnapshotCutoff = false,
            ),
            totalSourceResultsToDelete = queryCleanupCount(
                connection = connection,
                sql = RunHistorySql.countCleanupSourceResults(normalizedSchema),
                columnName = "total_source_results_to_delete",
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
                includeSnapshotCutoff = false,
            ),
            totalEventsToDelete = queryCleanupCount(
                connection = connection,
                sql = RunHistorySql.countCleanupEvents(normalizedSchema),
                columnName = "total_events_to_delete",
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
                includeSnapshotCutoff = false,
            ),
            totalArtifactsToDelete = queryCleanupCount(
                connection = connection,
                sql = RunHistorySql.countCleanupArtifacts(normalizedSchema),
                columnName = "total_artifacts_to_delete",
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
                includeSnapshotCutoff = false,
            ),
            totalOrphanExecutionSnapshotsToDelete = queryCleanupCount(
                connection = connection,
                sql = RunHistorySql.countCleanupOrphanExecutionSnapshots(normalizedSchema),
                columnName = "total_orphan_execution_snapshots_to_delete",
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
                includeSnapshotCutoff = true,
            ),
        )
    }

    fun listOutputRetentionCandidates(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef> =
        prepareCleanupStatement(
            connection = connection,
            sql = RunHistorySql.listCleanupOutputRetentionCandidates(normalizedSchema),
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            OutputRetentionRunRef(
                                moduleCode = rs.getString("module_code"),
                                requestedAt = rs.getTimestamp("requested_at").toInstant(),
                                outputDir = rs.getString("output_dir"),
                            ),
                        )
                    }
                }
            }
        }

    fun prepareCleanupStatement(
        connection: Connection,
        sql: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
        includeSnapshotCutoff: Boolean = false,
    ): PreparedStatement =
        connection.prepareStatement(sql).apply {
            var index = 1
            setTimestamp(index++, Timestamp.from(cutoffTimestamp))
            setBoolean(index++, disableSafeguard)
            setInt(index++, keepMinRunsPerModule)
            if (includeSnapshotCutoff) {
                setTimestamp(index, Timestamp.from(cutoffTimestamp))
            }
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
        prepareCleanupStatement(
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

internal data class DatabaseRunHistoryCleanupPreviewData(
    val modules: List<DatabaseRunHistoryCleanupModuleResponse>,
    val totalRunsToDelete: Int,
    val totalSourceResultsToDelete: Int,
    val totalEventsToDelete: Int,
    val totalArtifactsToDelete: Int,
    val totalOrphanExecutionSnapshotsToDelete: Int,
)

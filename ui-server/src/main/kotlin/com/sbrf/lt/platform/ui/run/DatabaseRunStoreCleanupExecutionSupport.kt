package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreCleanupExecutionSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
    private val cleanupPlanningSupport: DatabaseRunStoreCleanupPlanningSupport,
) {
    fun executeCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupResultResponse {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val preview = cleanupPlanningSupport.loadCleanupPreview(
                    connection = connection,
                    cutoffTimestamp = cutoffTimestamp,
                    keepMinRunsPerModule = keepMinRunsPerModule,
                    disableSafeguard = disableSafeguard,
                )

                cleanupPlanningSupport.prepareCleanupStatement(
                    connection = connection,
                    sql = RunHistorySql.deleteCleanupRuns(normalizedSchema),
                    cutoffTimestamp = cutoffTimestamp,
                    keepMinRunsPerModule = keepMinRunsPerModule,
                    disableSafeguard = disableSafeguard,
                ).use { stmt ->
                    stmt.executeUpdate()
                }

                val deletedOrphanSnapshots = connection.prepareStatement(
                    RunHistorySql.deleteCleanupOrphanExecutionSnapshots(normalizedSchema),
                ).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(cutoffTimestamp))
                    stmt.executeUpdate()
                }

                connection.commit()
                return DatabaseRunHistoryCleanupResultResponse(
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
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }
}

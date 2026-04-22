package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreCleanupExecutionSupport(
    private val transactionSupport: DatabaseRunStoreCleanupTransactionSupport,
    private val cleanupPlanningSupport: DatabaseRunStoreCleanupPlanningSupport,
    private val responseSupport: DatabaseRunStoreCleanupResponseSupport,
) {
    fun executeCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupResultResponse =
        transactionSupport.inTransaction { connection ->
            val preview = cleanupPlanningSupport.loadCleanupPreview(
                connection = connection,
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            )

            cleanupPlanningSupport.prepareCleanupStatement(
                connection = connection,
                sql = RunHistorySql.deleteCleanupRuns(cleanupPlanningSupport.normalizedSchema()),
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            ).use { stmt ->
                stmt.executeUpdate()
            }

            val deletedOrphanSnapshots = connection.prepareStatement(
                RunHistorySql.deleteCleanupOrphanExecutionSnapshots(cleanupPlanningSupport.normalizedSchema()),
            ).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(cutoffTimestamp))
                stmt.executeUpdate()
            }

            responseSupport.buildResultResponse(
                preview = preview,
                deletedOrphanSnapshots = deletedOrphanSnapshots,
                cutoffTimestamp = cutoffTimestamp,
                retentionDays = retentionDays,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            )
        }
}

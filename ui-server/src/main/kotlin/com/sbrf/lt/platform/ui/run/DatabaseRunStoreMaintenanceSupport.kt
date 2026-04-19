package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.time.Instant

internal class DatabaseRunStoreMaintenanceSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
) : DatabaseRunMaintenanceStore {
    private val normalizedSchema = normalizeRegistrySchemaName(schema)
    private val cleanupPlanningSupport = DatabaseRunStoreCleanupPlanningSupport(normalizedSchema)
    private val historyUsageSupport = DatabaseRunStoreHistoryUsageSupport(normalizedSchema)
    private val cleanupExecutionSupport = DatabaseRunStoreCleanupExecutionSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
        cleanupPlanningSupport = cleanupPlanningSupport,
    )

    override fun previewCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewResponse {
        connectionProvider.getConnection().use { connection ->
            val preview = cleanupPlanningSupport.loadCleanupPreview(
                connection = connection,
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            )
            val currentUsage = historyUsageSupport.loadCurrentHistoryStorageUsage(connection)
            return DatabaseRunHistoryCleanupPreviewResponse(
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
        }
    }

    override fun executeCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupResultResponse =
        cleanupExecutionSupport.executeCleanup(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )

    override fun listOutputRetentionCandidates(
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef> {
        connectionProvider.getConnection().use { connection ->
            return cleanupPlanningSupport.listOutputRetentionCandidates(
                connection = connection,
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            )
        }
    }

    override fun listCurrentOutputUsageCandidates(): List<OutputRetentionRunRef> {
        connectionProvider.getConnection().use { connection ->
            return historyUsageSupport.listCurrentOutputUsageCandidates(connection)
        }
    }
}

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
    private val responseSupport = DatabaseRunStoreCleanupResponseSupport()
    private val cleanupExecutionSupport = DatabaseRunStoreCleanupExecutionSupport(
        transactionSupport = DatabaseRunStoreCleanupTransactionSupport(connectionProvider),
        cleanupPlanningSupport = cleanupPlanningSupport,
        responseSupport = responseSupport,
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
            return responseSupport.buildPreviewResponse(
                preview = preview,
                currentUsage = currentUsage,
                cutoffTimestamp = cutoffTimestamp,
                retentionDays = retentionDays,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
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

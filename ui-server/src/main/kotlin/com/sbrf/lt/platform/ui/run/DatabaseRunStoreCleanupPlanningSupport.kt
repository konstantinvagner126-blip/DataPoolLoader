package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupPlanningSupport(
    private val normalizedSchema: String,
) {
    private val statementSupport = DatabaseRunStoreCleanupStatementSupport()
    private val previewSupport = DatabaseRunStoreCleanupPreviewSupport(normalizedSchema, statementSupport)
    private val candidateSupport = DatabaseRunStoreCleanupCandidateSupport(normalizedSchema, statementSupport)

    fun loadCleanupPreview(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewData =
        previewSupport.loadCleanupPreview(connection, cutoffTimestamp, keepMinRunsPerModule, disableSafeguard)

    fun listOutputRetentionCandidates(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef> =
        candidateSupport.listOutputRetentionCandidates(connection, cutoffTimestamp, keepMinRunsPerModule, disableSafeguard)

    fun prepareCleanupStatement(
        connection: Connection,
        sql: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
        includeSnapshotCutoff: Boolean = false,
    ) = statementSupport.prepareCleanupStatement(
        connection = connection,
        sql = sql,
        cutoffTimestamp = cutoffTimestamp,
        keepMinRunsPerModule = keepMinRunsPerModule,
        disableSafeguard = disableSafeguard,
        includeSnapshotCutoff = includeSnapshotCutoff,
    )
}

internal data class DatabaseRunHistoryCleanupPreviewData(
    val modules: List<DatabaseRunHistoryCleanupModuleResponse>,
    val totalRunsToDelete: Int,
    val totalSourceResultsToDelete: Int,
    val totalEventsToDelete: Int,
    val totalArtifactsToDelete: Int,
    val totalOrphanExecutionSnapshotsToDelete: Int,
)

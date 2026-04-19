package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreMaintenanceSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
) : DatabaseRunMaintenanceStore {

    override fun previewCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewResponse {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val preview = loadCleanupPreview(
                connection = connection,
                normalizedSchema = normalizedSchema,
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            )
            val currentUsage = loadCurrentHistoryStorageUsage(connection, normalizedSchema)
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
    ): DatabaseRunHistoryCleanupResultResponse {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val preview = loadCleanupPreview(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    cutoffTimestamp = cutoffTimestamp,
                    keepMinRunsPerModule = keepMinRunsPerModule,
                    disableSafeguard = disableSafeguard,
                )

                prepareCleanupStatement(
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

    override fun listOutputRetentionCandidates(
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            return prepareCleanupStatement(
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
        }
    }

    override fun listCurrentOutputUsageCandidates(): List<OutputRetentionRunRef> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            return connection.prepareStatement(
                RunHistorySql.listCurrentOutputUsageCandidates(normalizedSchema),
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
        }
    }

    private fun loadCleanupPreview(
        connection: Connection,
        normalizedSchema: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreview {
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

        return DatabaseRunHistoryCleanupPreview(
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

    private fun loadCurrentHistoryStorageUsage(
        connection: Connection,
        normalizedSchema: String,
    ): DatabaseHistoryStorageUsage {
        val overview = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageOverview(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    DatabaseHistoryStorageUsage()
                } else {
                    DatabaseHistoryStorageUsage(
                        totalRuns = rs.getInt("total_runs"),
                        totalModules = rs.getInt("total_modules"),
                        oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                        newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                    )
                }
            }
        }
        val totalBytes = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageBytes(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0L else rs.getLong("total_history_storage_bytes")
            }
        }
        val topModules = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageTopModules(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            CurrentStorageModuleResponse(
                                moduleCode = rs.getString("module_code"),
                                currentRunsCount = rs.getInt("current_runs_count"),
                                currentStorageBytes = rs.getLong("current_storage_bytes"),
                                oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                                newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                            ),
                        )
                    }
                }
            }
        }
        return overview.copy(totalStorageBytes = totalBytes, topModules = topModules)
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
                if (!rs.next()) {
                    0
                } else {
                    rs.getInt(columnName)
                }
            }
        }

    private fun prepareCleanupStatement(
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
}

private data class DatabaseRunHistoryCleanupPreview(
    val modules: List<DatabaseRunHistoryCleanupModuleResponse>,
    val totalRunsToDelete: Int,
    val totalSourceResultsToDelete: Int,
    val totalEventsToDelete: Int,
    val totalArtifactsToDelete: Int,
    val totalOrphanExecutionSnapshotsToDelete: Int,
)

private data class DatabaseHistoryStorageUsage(
    val totalRuns: Int = 0,
    val totalModules: Int = 0,
    val totalStorageBytes: Long = 0,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
    val topModules: List<CurrentStorageModuleResponse> = emptyList(),
)

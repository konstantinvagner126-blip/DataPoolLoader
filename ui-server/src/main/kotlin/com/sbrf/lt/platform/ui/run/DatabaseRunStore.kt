package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunArtifactResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Доступ к таблицам DB run-history.
 */
open class DatabaseRunStore(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val objectMapperWithTime: ObjectMapper = objectMapper
        .copy()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseRunStore =
            DatabaseRunStore(
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(config.jdbcUrl),
                    requireNotNull(config.username),
                    requireNotNull(config.password),
                ),
                schema = config.schemaName(),
            )
    }

    open fun hasActiveRun(moduleCode: String): Boolean {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.hasActiveRun(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.executeQuery().use { rs ->
                    return rs.next() && rs.getInt("active_runs") > 0
                }
            }
        }
    }

    open fun activeRunIds(moduleCode: String): List<String> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listActiveRunIds(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<String>()
                    while (rs.next()) {
                        result += rs.getString("run_id")
                    }
                    return result
                }
            }
        }
    }

    open fun activeModuleCodes(): Set<String> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listActiveModuleCodes(normalizedSchema)).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = linkedSetOf<String>()
                    while (rs.next()) {
                        result += rs.getString("module_code")
                    }
                    return result
                }
            }
        }
    }

    internal open fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(RunHistorySql.insertRun(normalizedSchema)).use { stmt ->
                    stmt.setString(1, context.runId)
                    stmt.setString(2, context.actorId)
                    stmt.setString(3, context.actorSource)
                    stmt.setString(4, context.actorDisplayName)
                    stmt.setTimestamp(5, Timestamp.from(context.requestedAt))
                    stmt.setTimestamp(6, Timestamp.from(startedAt))
                    stmt.setString(7, context.runtimeSnapshot.launchSourceKind)
                    stmt.setString(8, context.runtimeSnapshot.moduleCode ?: error("moduleCode is required for DB run"))
                    stmt.setString(9, context.runtimeSnapshot.moduleTitle ?: context.runtimeSnapshot.moduleCode ?: "DB module")
                    stmt.setString(10, outputDir)
                    stmt.setString(11, context.runtimeSnapshot.appConfig.mergeMode.name)
                    stmt.setBoolean(12, context.runtimeSnapshot.appConfig.target.enabled)
                    stmt.setString(13, if (context.runtimeSnapshot.appConfig.target.enabled) "PENDING" else "NOT_ENABLED")
                    stmt.setString(14, context.runtimeSnapshot.appConfig.target.table.takeIf { it.isNotBlank() })
                    stmt.setString(15, context.runtimeSnapshot.executionSnapshotId ?: error("executionSnapshotId is required for DB run"))
                    stmt.executeUpdate()
                }

                context.sourceOrder.entries.sortedBy { it.value }.forEach { (sourceName, sortOrder) ->
                    connection.prepareStatement(RunHistorySql.insertSourceResult(normalizedSchema)).use { stmt ->
                        stmt.setString(1, UUID.randomUUID().toString())
                        stmt.setString(2, context.runId)
                        stmt.setString(3, sourceName)
                        stmt.setInt(4, sortOrder)
                        stmt.executeUpdate()
                    }
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    open fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceStarted(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(startedAt))
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceProgress(normalizedSchema)).use { stmt ->
                stmt.setLong(1, exportedRowCount)
                stmt.setTimestamp(2, Timestamp.from(timestamp))
                stmt.setString(3, runId)
                stmt.setString(4, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun markSourceFinished(
        runId: String,
        sourceName: String,
        status: String,
        finishedAt: Instant,
        exportedRowCount: Long?,
        errorMessage: String?,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceFinished(normalizedSchema)).use { stmt ->
                stmt.setString(1, status)
                stmt.setTimestamp(2, Timestamp.from(finishedAt))
                setNullableLong(stmt, 3, exportedRowCount)
                stmt.setString(4, errorMessage)
                stmt.setString(5, runId)
                stmt.setString(6, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceMismatch(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(finishedAt))
                stmt.setString(2, message)
                stmt.setString(3, runId)
                stmt.setString(4, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceMergedRows(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateMergedRowCount(runId: String, mergedRowCount: Long) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateMergedRowCount(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateTargetStatus(normalizedSchema)).use { stmt ->
                stmt.setString(1, targetStatus)
                stmt.setString(2, targetTableName)
                setNullableLong(stmt, 3, targetRowsLoaded)
                stmt.setString(4, runId)
                stmt.executeUpdate()
            }
        }
    }

    open fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.insertEvent(normalizedSchema)).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, runId)
                stmt.setInt(3, seqNo)
                stmt.setString(4, stage)
                stmt.setString(5, eventType)
                stmt.setString(6, severity)
                stmt.setString(7, sourceName)
                stmt.setString(8, message)
                stmt.setString(9, objectMapperWithTime.writeValueAsString(payload))
                stmt.executeUpdate()
            }
        }
    }

    open fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.upsertArtifact(normalizedSchema)).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, runId)
                stmt.setString(3, artifactKind)
                stmt.setString(4, artifactKey)
                stmt.setString(5, filePath)
                stmt.setString(6, storageStatus)
                setNullableLong(stmt, 7, fileSizeBytes)
                stmt.setString(8, contentHash)
                stmt.executeUpdate()
            }
        }
    }

    open fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.markArtifactDeleted(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.setString(2, artifactKind)
                stmt.setString(3, artifactKey)
                stmt.executeUpdate()
            }
        }
    }

    open fun finishRun(
        runId: String,
        finishedAt: Instant,
        status: String,
        mergedRowCount: Long?,
        successfulSourceCount: Int,
        failedSourceCount: Int,
        skippedSourceCount: Int,
        targetStatus: String,
        targetTableName: String?,
        targetRowsLoaded: Long?,
        summaryJson: String,
        errorMessage: String?,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.finishRun(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(finishedAt))
                stmt.setString(2, status)
                setNullableLong(stmt, 3, mergedRowCount)
                stmt.setInt(4, successfulSourceCount)
                stmt.setInt(5, failedSourceCount)
                stmt.setInt(6, skippedSourceCount)
                stmt.setString(7, targetStatus)
                stmt.setString(8, targetTableName)
                setNullableLong(stmt, 9, targetRowsLoaded)
                stmt.setString(10, summaryJson)
                stmt.setString(11, errorMessage)
                stmt.setString(12, runId)
                stmt.executeUpdate()
            }
        }
    }

    open fun listRuns(moduleCode: String, limit: Int = 20): List<DatabaseModuleRunSummaryResponse> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listRuns(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseModuleRunSummaryResponse>()
                    while (rs.next()) {
                        result += DatabaseModuleRunSummaryResponse(
                            runId = rs.getString("run_id"),
                            executionSnapshotId = rs.getString("execution_snapshot_id"),
                            status = rs.getString("status"),
                            launchSourceKind = rs.getString("launch_source_kind"),
                            requestedAt = rs.getTimestamp("requested_at").toInstant(),
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                            moduleCode = rs.getString("module_code_snapshot"),
                            moduleTitle = rs.getString("module_title_snapshot"),
                            outputDir = rs.getString("output_dir"),
                            mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                            successfulSourceCount = rs.getInt("successful_source_count"),
                            failedSourceCount = rs.getInt("failed_source_count"),
                            skippedSourceCount = rs.getInt("skipped_source_count"),
                            targetStatus = rs.getString("target_status"),
                            targetTableName = rs.getString("target_table_name"),
                            targetRowsLoaded = rs.getLong("target_rows_loaded").takeIf { !rs.wasNull() },
                            errorMessage = rs.getString("error_message"),
                        )
                    }
                    return result
                }
            }
        }
    }

    open fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val summary = connection.prepareStatement(RunHistorySql.loadRunDetails(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.setString(2, runId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        DatabaseModuleRunDetailsResponse(
                            run = DatabaseModuleRunSummaryResponse(
                                runId = rs.getString("run_id"),
                                executionSnapshotId = rs.getString("execution_snapshot_id"),
                                status = rs.getString("status"),
                                launchSourceKind = rs.getString("launch_source_kind"),
                                requestedAt = rs.getTimestamp("requested_at").toInstant(),
                                startedAt = rs.getTimestamp("started_at")?.toInstant(),
                                finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                                moduleCode = rs.getString("module_code_snapshot"),
                                moduleTitle = rs.getString("module_title_snapshot"),
                                outputDir = rs.getString("output_dir"),
                                mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                                successfulSourceCount = rs.getInt("successful_source_count"),
                                failedSourceCount = rs.getInt("failed_source_count"),
                                skippedSourceCount = rs.getInt("skipped_source_count"),
                                targetStatus = rs.getString("target_status"),
                                targetTableName = rs.getString("target_table_name"),
                                targetRowsLoaded = rs.getLong("target_rows_loaded").takeIf { !rs.wasNull() },
                                errorMessage = rs.getString("error_message"),
                            ),
                            summaryJson = rs.getString("summary_json") ?: "{}",
                            sourceResults = emptyList(),
                            events = emptyList(),
                            artifacts = emptyList(),
                        )
                    }
                }
            }

            requireNotNull(summary) {
                "История запуска '$runId' для DB-модуля '$moduleCode' не найдена."
            }

            val sourceResults = connection.prepareStatement(RunHistorySql.listRunSourceResults(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunSourceResultResponse>()
                    while (rs.next()) {
                        result += DatabaseRunSourceResultResponse(
                            runSourceResultId = rs.getString("run_source_result_id"),
                            sourceName = rs.getString("source_name"),
                            sortOrder = rs.getInt("sort_order"),
                            status = rs.getString("status"),
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                            exportedRowCount = rs.getLong("exported_row_count").takeIf { !rs.wasNull() },
                            mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                            errorMessage = rs.getString("error_message"),
                        )
                    }
                    result
                }
            }

            val events = connection.prepareStatement(RunHistorySql.listRunEvents(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunEventResponse>()
                    while (rs.next()) {
                        result += DatabaseRunEventResponse(
                            runEventId = rs.getString("run_event_id"),
                            seqNo = rs.getInt("seq_no"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                            stage = rs.getString("stage"),
                            eventType = rs.getString("event_type"),
                            severity = rs.getString("severity"),
                            sourceName = rs.getString("source_name"),
                            message = rs.getString("message"),
                            payloadJson = readJsonObject(rs.getString("payload_json")),
                        )
                    }
                    result
                }
            }

            val artifacts = connection.prepareStatement(RunHistorySql.listRunArtifacts(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunArtifactResponse>()
                    while (rs.next()) {
                        result += DatabaseRunArtifactResponse(
                            runArtifactId = rs.getString("run_artifact_id"),
                            artifactKind = rs.getString("artifact_kind"),
                            artifactKey = rs.getString("artifact_key"),
                            filePath = rs.getString("file_path"),
                            storageStatus = rs.getString("storage_status"),
                            fileSizeBytes = rs.getLong("file_size_bytes").takeIf { !rs.wasNull() },
                            contentHash = rs.getString("content_hash"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                        )
                    }
                    result
                }
            }

            return summary.copy(
                sourceResults = sourceResults,
                events = events,
                artifacts = artifacts,
            )
        }
    }

    open fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(RunHistorySql.markIncompleteSourcesFailed(normalizedSchema)).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(finishedAt))
                    stmt.setString(2, errorMessage)
                    stmt.setString(3, runId)
                    stmt.executeUpdate()
                }
                connection.prepareStatement(RunHistorySql.markRunFailed(normalizedSchema)).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(finishedAt))
                    stmt.setString(2, errorMessage)
                    stmt.setString(3, runId)
                    stmt.executeUpdate()
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    open fun previewCleanup(
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
            return DatabaseRunHistoryCleanupPreviewResponse(
                safeguardEnabled = !disableSafeguard,
                retentionDays = retentionDays,
                keepMinRunsPerModule = keepMinRunsPerModule,
                cutoffTimestamp = cutoffTimestamp,
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

    internal open fun listOutputRetentionCandidates(
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

    open fun executeCleanup(
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

    private data class DatabaseRunHistoryCleanupPreview(
        val modules: List<DatabaseRunHistoryCleanupModuleResponse>,
        val totalRunsToDelete: Int,
        val totalSourceResultsToDelete: Int,
        val totalEventsToDelete: Int,
        val totalArtifactsToDelete: Int,
        val totalOrphanExecutionSnapshotsToDelete: Int,
    )

    private fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
        if (value == null) {
            stmt.setObject(index, null)
        } else {
            stmt.setLong(index, value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readJsonObject(rawJson: String?): Map<String, Any?> {
        if (rawJson.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapperWithTime.readValue(rawJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
    }

    open fun fileSize(filePath: Path): Long? = Files.size(filePath)
}

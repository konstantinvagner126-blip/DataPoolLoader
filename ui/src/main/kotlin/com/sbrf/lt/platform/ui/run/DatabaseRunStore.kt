package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.module.DatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.normalizeSchemaName
import java.nio.file.Files
import java.nio.file.Path
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
                connectionProvider = DriverManagerDatabaseConnectionProvider(config),
                schema = config.schemaName(),
            )
    }

    open fun hasActiveRun(moduleCode: String): Boolean {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.hasActiveRun(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.executeQuery().use { rs ->
                    return rs.next() && rs.getInt("active_runs") > 0
                }
            }
        }
    }

    open fun activeRunIds(moduleCode: String): List<String> {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.listActiveRunIds(normalizedSchema)).use { stmt ->
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

    internal open fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(DatabaseRunStoreSql.insertRun(normalizedSchema)).use { stmt ->
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
                    connection.prepareStatement(DatabaseRunStoreSql.insertSourceResult(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateSourceStarted(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(startedAt))
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateSourceFinished(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateSourceMismatch(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(finishedAt))
                stmt.setString(2, message)
                stmt.setString(3, runId)
                stmt.setString(4, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateSourceMergedRows(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateMergedRowCount(runId: String, mergedRowCount: Long) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateMergedRowCount(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
        }
    }

    open fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.updateTargetStatus(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.insertEvent(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.upsertArtifact(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.markArtifactDeleted(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.finishRun(normalizedSchema)).use { stmt ->
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
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(DatabaseRunStoreSql.listRuns(normalizedSchema)).use { stmt ->
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

    open fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    ) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(DatabaseRunStoreSql.markIncompleteSourcesFailed(normalizedSchema)).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(finishedAt))
                    stmt.setString(2, errorMessage)
                    stmt.setString(3, runId)
                    stmt.executeUpdate()
                }
                connection.prepareStatement(DatabaseRunStoreSql.markRunFailed(normalizedSchema)).use { stmt ->
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

    private fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
        if (value == null) {
            stmt.setObject(index, null)
        } else {
            stmt.setLong(index, value)
        }
    }

    open fun fileSize(filePath: Path): Long? = Files.size(filePath)
}

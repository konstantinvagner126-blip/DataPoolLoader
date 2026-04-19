package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

internal class DatabaseRunStoreExecutionSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
    private val objectMapperWithTime: ObjectMapper,
) : DatabaseRunExecutionStore {

    override fun hasActiveRun(moduleCode: String): Boolean {
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

    override fun activeRunIds(moduleCode: String): List<String> {
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

    override fun createRun(
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

    override fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant) {
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

    override fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long) {
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

    override fun markSourceFinished(
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

    override fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String) {
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

    override fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) {
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

    override fun updateMergedRowCount(runId: String, mergedRowCount: Long) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateMergedRowCount(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
        }
    }

    override fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) {
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

    override fun appendEvent(
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

    override fun upsertArtifact(
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

    override fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) {
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

    override fun finishRun(
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

    override fun markRunFailed(
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

    override fun fileSize(filePath: Path): Long? = Files.size(filePath)
}

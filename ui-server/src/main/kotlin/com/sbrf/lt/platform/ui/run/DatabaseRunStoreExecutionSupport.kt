package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import java.nio.file.Path
import java.time.Instant

internal class DatabaseRunStoreExecutionSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
    private val objectMapperWithTime: ObjectMapper,
) : DatabaseRunExecutionStore {
    private val normalizedSchema = normalizeRegistrySchemaName(schema)
    private val runLifecycleSupport = DatabaseRunStoreRunLifecycleSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
    )
    private val eventArtifactSupport = DatabaseRunStoreEventArtifactSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
        objectMapperWithTime = objectMapperWithTime,
    )
    private val progressUpdateSupport = DatabaseRunStoreProgressUpdateSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
    )

    override fun hasActiveRun(moduleCode: String): Boolean = runLifecycleSupport.hasActiveRun(moduleCode)

    override fun activeRunIds(moduleCode: String): List<String> = runLifecycleSupport.activeRunIds(moduleCode)

    override fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) = runLifecycleSupport.createRun(context, startedAt, outputDir)

    override fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant) =
        progressUpdateSupport.markSourceStarted(runId, sourceName, startedAt)

    override fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long) =
        progressUpdateSupport.updateSourceProgress(runId, sourceName, timestamp, exportedRowCount)

    override fun markSourceFinished(
        runId: String,
        sourceName: String,
        status: String,
        finishedAt: Instant,
        exportedRowCount: Long?,
        errorMessage: String?,
    ) = progressUpdateSupport.markSourceFinished(runId, sourceName, status, finishedAt, exportedRowCount, errorMessage)

    override fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String) =
        progressUpdateSupport.markSourceSkipped(runId, sourceName, finishedAt, message)

    override fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) =
        progressUpdateSupport.updateSourceMergedRows(runId, sourceName, mergedRowCount)

    override fun updateMergedRowCount(runId: String, mergedRowCount: Long) =
        progressUpdateSupport.updateMergedRowCount(runId, mergedRowCount)

    override fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) =
        progressUpdateSupport.updateTargetStatus(runId, targetStatus, targetTableName, targetRowsLoaded)

    override fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) = eventArtifactSupport.appendEvent(runId, seqNo, stage, eventType, severity, sourceName, message, payload)

    override fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    ) = eventArtifactSupport.upsertArtifact(runId, artifactKind, artifactKey, filePath, storageStatus, fileSizeBytes, contentHash)

    override fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) =
        eventArtifactSupport.markArtifactDeleted(runId, artifactKind, artifactKey)

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
    ) = runLifecycleSupport.finishRun(
        runId = runId,
        finishedAt = finishedAt,
        status = status,
        mergedRowCount = mergedRowCount,
        successfulSourceCount = successfulSourceCount,
        failedSourceCount = failedSourceCount,
        skippedSourceCount = skippedSourceCount,
        targetStatus = targetStatus,
        targetTableName = targetTableName,
        targetRowsLoaded = targetRowsLoaded,
        summaryJson = summaryJson,
        errorMessage = errorMessage,
    )

    override fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    ) = runLifecycleSupport.markRunFailed(runId, finishedAt, errorMessage)

    override fun fileSize(filePath: Path): Long? = eventArtifactSupport.fileSize(filePath)
}

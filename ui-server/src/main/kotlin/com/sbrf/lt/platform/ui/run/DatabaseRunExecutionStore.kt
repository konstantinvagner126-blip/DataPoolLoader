package com.sbrf.lt.platform.ui.run

import java.nio.file.Path
import java.time.Instant

/**
 * Контракт выполнения DB-run и записи live-состояния.
 */
interface DatabaseRunExecutionStore {
    fun hasActiveRun(moduleCode: String): Boolean

    fun activeRunIds(moduleCode: String): List<String>

    fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    )

    fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant)

    fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long)

    fun markSourceFinished(
        runId: String,
        sourceName: String,
        status: String,
        finishedAt: Instant,
        exportedRowCount: Long?,
        errorMessage: String?,
    )

    fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String)

    fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long)

    fun updateMergedRowCount(runId: String, mergedRowCount: Long)

    fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?)

    fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    )

    fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    )

    fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String)

    fun finishRun(
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
    )

    fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    )

    fun fileSize(filePath: Path): Long?
}

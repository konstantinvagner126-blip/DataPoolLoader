package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse
import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import com.sbrf.lt.platform.ui.model.ModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleEditorSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleRunEventResponse
import com.sbrf.lt.platform.ui.model.ModuleRunHistoryResponse
import com.sbrf.lt.platform.ui.model.ModuleRunPageSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSummaryResponse

internal fun buildDatabaseModuleRunPageSession(
    session: ModuleEditorSessionResponse,
): ModuleRunPageSessionResponse =
    ModuleRunPageSessionResponse(
        storageMode = "DATABASE",
        moduleId = session.module.id,
        moduleTitle = session.module.descriptor.title,
        moduleMeta = "${session.module.configPath} · режим хранения: База данных",
    )

internal fun buildDatabaseModuleRunHistoryResponse(
    moduleId: String,
    response: DatabaseModuleRunsResponse,
): ModuleRunHistoryResponse =
    ModuleRunHistoryResponse(
        storageMode = "DATABASE",
        moduleId = moduleId,
        activeRunId = response.runs.firstOrNull { it.status == "RUNNING" }?.runId,
        runs = response.runs.map { it.toCommon() },
    )

internal fun buildDatabaseModuleRunDetailsResponse(
    response: DatabaseModuleRunDetailsResponse,
): ModuleRunDetailsResponse =
    ModuleRunDetailsResponse(
        run = response.run.toCommon(),
        summaryJson = response.summaryJson,
        sourceResults = response.sourceResults.map { it.toCommon() },
        events = response.events.map { it.toCommon() },
        artifacts = response.artifacts.map { it.toCommon() },
    )

private fun com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse.toCommon() =
    ModuleRunSummaryResponse(
        runId = runId,
        moduleId = moduleCode,
        moduleTitle = moduleTitle,
        status = status,
        startedAt = startedAt ?: requestedAt,
        finishedAt = finishedAt,
        requestedAt = requestedAt,
        outputDir = outputDir,
        mergedRowCount = mergedRowCount,
        errorMessage = errorMessage,
        launchSourceKind = launchSourceKind,
        executionSnapshotId = executionSnapshotId,
        successfulSourceCount = successfulSourceCount,
        failedSourceCount = failedSourceCount,
        skippedSourceCount = skippedSourceCount,
        targetStatus = targetStatus,
        targetTableName = targetTableName,
        targetRowsLoaded = targetRowsLoaded,
    )

private fun com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse.toCommon() =
    ModuleRunSourceResultResponse(
        runSourceResultId = runSourceResultId,
        sourceName = sourceName,
        sortOrder = sortOrder,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        exportedRowCount = exportedRowCount,
        mergedRowCount = mergedRowCount,
        errorMessage = errorMessage,
    )

private fun com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse.toCommon() =
    ModuleRunEventResponse(
        runEventId = runEventId,
        seqNo = seqNo,
        timestamp = createdAt,
        stage = stage,
        eventType = eventType,
        severity = severity,
        sourceName = sourceName,
        message = message,
        payload = payloadJson,
    )

private fun com.sbrf.lt.platform.ui.model.DatabaseRunArtifactResponse.toCommon() =
    ModuleRunArtifactResponse(
        runArtifactId = runArtifactId,
        artifactKind = artifactKind,
        artifactKey = artifactKey,
        filePath = filePath,
        storageStatus = storageStatus,
        fileSizeBytes = fileSizeBytes,
        contentHash = contentHash,
        createdAt = createdAt,
    )

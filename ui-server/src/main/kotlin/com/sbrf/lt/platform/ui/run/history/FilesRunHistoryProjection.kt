package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import com.sbrf.lt.platform.ui.model.ModuleRunEventResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import java.time.Instant

internal fun projectFilesRunSourceResults(run: UiRunSnapshot): List<ModuleRunSourceResultResponse> {
    val states = linkedMapOf<String, FilesSourceState>()
    var sortOrder = 0

    run.events.forEach { event ->
        val sourceName = event.eventSourceName() ?: return@forEach
        val state = states.getOrPut(sourceName) { FilesSourceState(sortOrder = sortOrder++) }
        when (detectFilesEventType(event)) {
            "SourceExportStartedEvent" -> {
                state.startedAt = event.eventTimestamp()
                state.status = "RUNNING"
            }
            "SourceExportProgressEvent" -> {
                state.exportedRowCount = event.eventRowCount() ?: state.exportedRowCount
                if (state.status == "PENDING") {
                    state.status = "RUNNING"
                }
            }
            "SourceExportFinishedEvent" -> {
                state.finishedAt = event.eventTimestamp()
                state.status = event.statusValue() ?: state.status
                state.exportedRowCount = event.eventRowCount() ?: state.exportedRowCount
                state.errorMessage = event.eventErrorMessage()
            }
            "SourceSchemaMismatchEvent" -> {
                state.finishedAt = event.eventTimestamp()
                state.status = "SKIPPED"
                state.errorMessage = "Источник исключен из объединения из-за несовпадения схемы."
            }
            "MergeFinishedEvent" -> {
                state.mergedRowCount = event.sourceCounts()[sourceName] ?: state.mergedRowCount
            }
        }
    }

    return states.entries.map { (sourceName, state) ->
        ModuleRunSourceResultResponse(
            sourceName = sourceName,
            sortOrder = state.sortOrder,
            status = state.status,
            startedAt = state.startedAt,
            finishedAt = state.finishedAt,
            exportedRowCount = state.exportedRowCount,
            mergedRowCount = state.mergedRowCount,
            errorMessage = state.errorMessage,
        )
    }
}

internal data class FilesTargetState(
    val enabled: Boolean,
    val status: String,
    val tableName: String? = null,
    val rowsLoaded: Long? = null,
)

internal fun projectFilesTargetState(run: UiRunSnapshot): FilesTargetState {
    var targetEnabled = false
    var targetStatus = if (run.status.name == "FAILED") "FAILED" else "NOT_ENABLED"
    var targetTableName: String? = null
    var targetRowsLoaded: Long? = null

    run.events.forEach { event ->
        when (detectFilesEventType(event)) {
            "RunStartedEvent" -> {
                targetEnabled = event.targetEnabledValue() == true
                if (targetEnabled && targetStatus == "NOT_ENABLED") {
                    targetStatus = if (run.status.name == "FAILED") "FAILED" else "PENDING"
                }
            }

            "TargetImportStartedEvent" -> {
                targetEnabled = true
                targetStatus = if (run.status.name == "FAILED") "FAILED" else "RUNNING"
                targetTableName = event.eventTableName() ?: targetTableName
            }

            "TargetImportFinishedEvent" -> {
                targetEnabled = true
                targetStatus = event.statusValue() ?: targetStatus
                targetTableName = event.eventTableName() ?: targetTableName
                targetRowsLoaded = event.eventRowCount() ?: targetRowsLoaded
            }
        }
    }

    if (!targetEnabled) {
        return FilesTargetState(
            enabled = false,
            status = "NOT_ENABLED",
        )
    }

    if (run.status.name == "SUCCESS" && targetStatus == "RUNNING") {
        targetStatus = "SUCCESS"
    } else if (run.status.name == "FAILED" && targetStatus == "PENDING") {
        targetStatus = "FAILED"
    }

    return FilesTargetState(
        enabled = true,
        status = targetStatus,
        tableName = targetTableName,
        rowsLoaded = targetRowsLoaded,
    )
}

internal fun projectFilesRunArtifacts(
    run: UiRunSnapshot,
    sourceResults: List<ModuleRunSourceResultResponse>,
): List<ModuleRunArtifactResponse> {
    val outputDir = run.outputDir ?: return emptyList()
    val artifacts = mutableListOf<ModuleRunArtifactResponse>()

    artifacts += createArtifact(
        artifactKind = "MERGED_OUTPUT",
        artifactKey = "merged",
        filePath = joinFilesRunOutputPath(outputDir, "merged.csv"),
    )

    if (!run.summaryJson.isNullOrBlank()) {
        artifacts += createArtifact(
            artifactKind = "SUMMARY_JSON",
            artifactKey = "summary",
            filePath = joinFilesRunOutputPath(outputDir, "summary.json"),
        )
    }

    sourceResults
        .filter { it.status == "SUCCESS" }
        .sortedBy { it.sortOrder }
        .forEach { source ->
            artifacts += createArtifact(
                artifactKind = "SOURCE_OUTPUT",
                artifactKey = source.sourceName,
                filePath = joinFilesRunOutputPath(outputDir, "${source.sourceName}.csv"),
            )
        }

    return artifacts
}

internal fun projectFilesRunEvents(run: UiRunSnapshot): List<ModuleRunEventResponse> =
    run.events.mapIndexedNotNull { index, event ->
        val eventType = detectFilesEventType(event) ?: return@mapIndexedNotNull null
        ModuleRunEventResponse(
            seqNo = index + 1,
            timestamp = event.eventTimestamp(),
            stage = filesStageFor(eventType),
            eventType = eventType,
            severity = filesSeverityFor(eventType, event),
            sourceName = event.eventSourceName(),
            message = filesMessageFor(eventType, event),
            payload = event,
        )
    }

private fun createArtifact(
    artifactKind: String,
    artifactKey: String,
    filePath: String,
): ModuleRunArtifactResponse =
    createFilesRunArtifact(
        artifactKind = artifactKind,
        artifactKey = artifactKey,
        filePath = filePath,
    )

private class FilesSourceState(
    val sortOrder: Int,
    var status: String = "PENDING",
    var startedAt: Instant? = null,
    var finishedAt: Instant? = null,
    var exportedRowCount: Long? = null,
    var mergedRowCount: Long? = null,
    var errorMessage: String? = null,
)

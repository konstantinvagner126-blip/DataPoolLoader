package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot

internal fun UiRunSnapshot.toCommonSummary(): ModuleRunSummaryResponse {
    val sourceResults = projectFilesRunSourceResults(this)
    val targetState = projectFilesTargetState(this)
    return ModuleRunSummaryResponse(
        runId = id,
        moduleId = moduleId,
        moduleTitle = moduleTitle,
        status = status.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        requestedAt = startedAt,
        outputDir = outputDir,
        mergedRowCount = mergedRowCount,
        errorMessage = errorMessage,
        successfulSourceCount = sourceResults.count { it.status == "SUCCESS" },
        failedSourceCount = sourceResults.count { it.status == "FAILED" },
        skippedSourceCount = sourceResults.count { it.status == "SKIPPED" },
        targetStatus = targetState.status,
        targetTableName = targetState.tableName,
        targetRowsLoaded = targetState.rowsLoaded,
    )
}

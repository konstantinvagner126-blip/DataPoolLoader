package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.run.formatStageDuration
import com.sbrf.lt.platform.composeui.run.StructuredRunSummary
import com.sbrf.lt.platform.composeui.run.detectRunStageKey

@Composable
internal fun ModuleRunDetailsPanel(
    details: ModuleRunDetailsResponse,
    history: ModuleRunHistoryResponse,
    structuredSummary: StructuredRunSummary?,
    showTechnicalDiagnostics: Boolean,
    onToggleTechnicalDiagnostics: () -> Unit,
) {
    val successCount = details.run.successfulSourceCount
        ?: details.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) }
    val failedCount = details.run.failedSourceCount
        ?: details.sourceResults.count { it.status.equals("FAILED", ignoreCase = true) }
    val skippedCount = details.run.skippedSourceCount
        ?: details.sourceResults.count { it.status.equals("SKIPPED", ignoreCase = true) }
    val warningCount = failedCount + skippedCount
    val currentStageKey = detectRunStageKey(details.run, details.events)
    val runDuration = formatDuration(
        details.run.startedAt,
        details.run.finishedAt,
        running = details.run.status.equals("RUNNING", ignoreCase = true),
    )
    val mergeDuration = formatStageDuration(
        details.events,
        stage = "MERGE",
        running = details.run.status.equals("RUNNING", ignoreCase = true) &&
            currentStageKey == "merge",
    )
    val targetDuration = formatStageDuration(
        details.events,
        stage = "TARGET",
        running = details.run.status.equals("RUNNING", ignoreCase = true) &&
            currentStageKey == "target",
    )

    RunOverviewSection(
        details = details,
        history = history,
        currentStageKey = currentStageKey,
        runDuration = runDuration,
        mergeDuration = mergeDuration,
        targetDuration = targetDuration,
        successCount = successCount,
        warningCount = warningCount,
    )
    StructuredSummarySection(structuredSummary)
    SourceAllocationSection(structuredSummary)
    FailedSourcesSection(structuredSummary)
    SourceResultsSection(details)
    RunEventsSection(details)
    TechnicalDiagnosticsSection(
        details = details,
        showTechnicalDiagnostics = showTechnicalDiagnostics,
        enabled = history.uiSettings.showTechnicalDiagnostics,
        onToggleTechnicalDiagnostics = onToggleTechnicalDiagnostics,
    )
    RunArtifactsSection(details)
    RawSummaryJsonSection(details, history)
}

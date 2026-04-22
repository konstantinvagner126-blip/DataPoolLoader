package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressMetric
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressWidget
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.buildRunProgressStages
import com.sbrf.lt.platform.composeui.foundation.component.eventEntryCssClass
import com.sbrf.lt.platform.composeui.foundation.component.runStatusCssClass
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.foundation.run.formatTimeoutSeconds
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.run.buildCompactProgressEntries
import com.sbrf.lt.platform.composeui.run.detectActiveSourceName
import com.sbrf.lt.platform.composeui.run.detectRunStageKey
import com.sbrf.lt.platform.composeui.run.parseStructuredRunSummary
import com.sbrf.lt.platform.composeui.run.translateRunStatus
import com.sbrf.lt.platform.composeui.run.translateStage
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun EditorRunOverviewPanel(
    route: ModuleEditorRouteState,
    state: ModuleRunsPageState,
) {
    SectionCard(
        title = "Ход выполнения",
        subtitle = "Компактный live-блок по текущему или последнему запуску. Полные детали остаются на отдельном экране.",
    ) {
        val history = state.history
        if (state.errorMessage != null) {
            AlertBanner(state.errorMessage ?: "", "warning")
        }

        when {
            state.loading && history == null -> {
                EditorRunMutedText("Загружаю информацию о запусках модуля.")
            }

            history == null || history.runs.isEmpty() || state.selectedRunDetails == null -> {
                EditorRunMutedText("Активного запуска сейчас нет. Детали прошлых запусков открываются на отдельном экране.")
            }

            else -> {
                val details = requireNotNull(state.selectedRunDetails)
                val structuredSummary = details.summaryJson?.let(::parseStructuredRunSummary)
                val currentStageKey = detectRunStageKey(details.run, details.events)
                val successCount = details.run.successfulSourceCount
                    ?: details.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) }
                val failedCount = details.run.failedSourceCount
                    ?: details.sourceResults.count { it.status.equals("FAILED", ignoreCase = true) }
                val warningCount = details.sourceResults.count { it.status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) }
                val latestEntries = buildCompactProgressEntries(details)
                val runIsActive = details.run.status.equals("RUNNING", ignoreCase = true)
                val activeSourceName = detectActiveSourceName(details.run, details.sourceResults, details.events)
                val subtitleParts = buildList {
                    add(translateStage(currentStageKey))
                    activeSourceName?.let { add("Источник: $it") }
                    add(formatDateTime(details.run.requestedAt ?: details.run.startedAt))
                }

                RunProgressWidget(
                    title = if (runIsActive) "Текущий запуск" else "Последний запуск",
                    subtitle = subtitleParts.joinToString(" · "),
                    statusLabel = translateRunStatus(details.run.status),
                    statusClassName = runStatusCssClass(details.run.status),
                    running = runIsActive,
                    stages = buildRunProgressStages(currentStageKey, details.run.status),
                    metrics = buildList {
                        if (!activeSourceName.isNullOrBlank()) {
                            add(RunProgressMetric("Активный источник", activeSourceName, tone = "primary"))
                        }
                        add(
                            RunProgressMetric(
                                "Длительность",
                                formatDuration(
                                    details.run.startedAt,
                                    details.run.finishedAt,
                                    running = runIsActive,
                                ),
                            ),
                        )
                        structuredSummary?.parallelism?.let {
                            add(RunProgressMetric("Параллелизм", formatNumber(it)))
                        }
                        structuredSummary?.fetchSize?.let {
                            add(RunProgressMetric("Fetch size", formatNumber(it)))
                        }
                        add(
                            RunProgressMetric(
                                "Query timeout",
                                formatTimeoutSeconds(structuredSummary?.queryTimeoutSec),
                            ),
                        )
                        add(RunProgressMetric("Строк в merged", formatNumber(details.run.mergedRowCount)))
                        add(RunProgressMetric("Успешных источников", formatNumber(successCount), tone = "success"))
                        add(RunProgressMetric("Ошибок", formatNumber(failedCount), tone = if (failedCount > 0) "danger" else "default"))
                        add(RunProgressMetric("Предупреждений", formatNumber(warningCount), tone = if (warningCount > 0) "warning" else "default"))
                    },
                )

                if (latestEntries.isNotEmpty()) {
                    Div({ classes("human-log", "mt-3") }) {
                        latestEntries.forEach { entry ->
                            Div({ classesFromString(eventEntryCssClass(entry.severity)) }) {
                                val parts = buildList {
                                    formatDateTime(entry.timestamp).takeIf { it != "-" }?.let(::add)
                                    entry.message.takeIf { it.isNotBlank() }?.let(::add)
                                }
                                Text(parts.joinToString(" · ").ifBlank { "-" })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorRunMutedText(
    text: String,
    extraClassName: String = "",
) {
    Div({
        classes("text-secondary", "small")
        if (extraClassName.isNotBlank()) {
            classes(extraClassName)
        }
    }) {
        Text(text)
    }
}

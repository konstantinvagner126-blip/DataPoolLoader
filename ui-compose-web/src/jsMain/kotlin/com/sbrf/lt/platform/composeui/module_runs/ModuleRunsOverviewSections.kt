package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressMetric
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressWidget
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.buildRunProgressStages
import com.sbrf.lt.platform.composeui.foundation.component.runStatusCssClass
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.foundation.run.formatBooleanFlag
import com.sbrf.lt.platform.composeui.foundation.run.formatRowsInterval
import com.sbrf.lt.platform.composeui.foundation.run.formatTimeoutSeconds
import com.sbrf.lt.platform.composeui.run.StructuredRunSummary
import com.sbrf.lt.platform.composeui.run.translateLaunchSource
import com.sbrf.lt.platform.composeui.run.translateRunStatus
import com.sbrf.lt.platform.composeui.run.translateStageKey
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun RunOverviewSection(
    details: ModuleRunDetailsResponse,
    history: ModuleRunHistoryResponse,
    currentStageKey: String,
    runDuration: String,
    mergeDuration: String,
    targetDuration: String,
    successCount: Int,
    warningCount: Int,
) {
    SectionCard(
        title = "Выбранный запуск",
        subtitle = "Краткая сводка по текущему запуску.",
    ) {
        Div({ classes("run-summary-header") }) {
            Div {
                Div({ classes("run-summary-title") }) {
                    Text(details.run.moduleTitle.ifBlank { details.run.moduleId })
                }
                Div({ classes("run-summary-subtitle") }) {
                    Text("Запуск ${details.run.runId} · ${formatDateTime(details.run.requestedAt ?: details.run.startedAt)}")
                }
            }
            Div({ classes("run-summary-metrics") }) {
                SummaryMetricBadge(
                    label = "Статус",
                    value = translateRunStatus(details.run.status),
                    running = details.run.status.equals("RUNNING", ignoreCase = true),
                )
                SummaryMetricBadge("Источник запуска", translateLaunchSource(details.run.launchSourceKind))
                SummaryMetricBadge("Target", translateRunStatus(details.run.targetStatus))
            }
        }
        RunProgressWidget(
            title = details.run.moduleTitle,
            subtitle = "${translateStageKey(currentStageKey)} · запуск ${details.run.runId}",
            statusLabel = translateRunStatus(details.run.status),
            statusClassName = runStatusCssClass(details.run.status),
            running = details.run.status.equals("RUNNING", ignoreCase = true),
            stages = buildRunProgressStages(currentStageKey, details.run.status),
            showStatus = false,
            metrics = listOf(
                RunProgressMetric(
                    label = "Строк в merged",
                    value = formatNumber(details.run.mergedRowCount),
                    tone = "important",
                ),
                RunProgressMetric(
                    label = "Длительность",
                    value = runDuration,
                    tone = if (runDuration != "-") "important" else "default",
                ),
                RunProgressMetric(
                    label = "Успешные источники",
                    value = formatNumber(successCount),
                    tone = if (successCount > 0) "success" else "default",
                ),
                RunProgressMetric(
                    label = "Предупреждения и ошибки",
                    value = formatNumber(warningCount),
                    tone = if (warningCount > 0) "failed" else "default",
                ),
                RunProgressMetric(
                    label = "Загружено",
                    value = formatNumber(details.run.targetRowsLoaded),
                    tone = if ((details.run.targetRowsLoaded ?: 0) > 0) "important" else "default",
                ),
            ),
        )
        Div({ classes("run-result-actions", "mt-3") }) {
            if (details.artifacts.isNotEmpty()) {
                org.jetbrains.compose.web.dom.A(attrs = {
                    classes("run-result-action-button")
                    href("#run-artifacts-section")
                }) {
                    Text("К результатам запуска")
                }
            }
            if (details.sourceResults.isNotEmpty()) {
                org.jetbrains.compose.web.dom.A(attrs = {
                    classes("run-result-action-button")
                    href("#run-source-results-section")
                }) {
                    Text("К результатам по источникам")
                }
            }
            if (history.uiSettings.showRawSummaryJson && !details.summaryJson.isNullOrBlank() && details.summaryJson != "{}") {
                org.jetbrains.compose.web.dom.A(attrs = {
                    classes("run-result-action-button")
                    href("#run-summary-json-section")
                }) {
                    Text("Открыть summary.json")
                }
            }
        }
        Div({ classes("run-summary-list", "mt-3") }) {
            SummaryRow("Запрошен", formatDateTime(details.run.requestedAt ?: details.run.startedAt))
            SummaryRow("Старт", formatDateTime(details.run.startedAt))
            SummaryRow("Завершение", formatDateTime(details.run.finishedAt))
            SummaryRow("Длительность", runDuration)
            SummaryRow("Длительность merge", mergeDuration)
            SummaryRow("Длительность target", targetDuration)
            SummaryRow("Источник запуска", translateLaunchSource(details.run.launchSourceKind))
            SummaryRow("Строк в merged", formatNumber(details.run.mergedRowCount))
            SummaryRow("Target", details.run.targetTableName ?: "-")
            SummaryRow("Статус target", translateRunStatus(details.run.targetStatus))
            SummaryRow("Загружено", formatNumber(details.run.targetRowsLoaded))
            SummaryRow("Каталог результата", details.run.outputDir ?: "-")
            SummaryRow("Снимок", details.run.executionSnapshotId ?: "-")
            SummaryRow("Ошибка", details.run.errorMessage ?: "-")
        }
    }
}

@Composable
internal fun StructuredSummarySection(structuredSummary: StructuredRunSummary?) {
    Div({
        attr("id", "run-structured-summary-section")
    }) {
        SectionCard(
            title = "Итог запуска",
            subtitle = "Структурированное summary выбранного запуска.",
        ) {
            if (structuredSummary == null) {
                P({ classes("text-secondary", "mb-0") }) { Text("Итоги запуска еще не сформированы.") }
            } else {
                SummaryRow("Режим объединения", structuredSummary.mergeMode ?: "-")
                SummaryRow("Параллелизм", formatNumber(structuredSummary.parallelism))
                SummaryRow("Fetch size", formatNumber(structuredSummary.fetchSize))
                SummaryRow("Query timeout", formatTimeoutSeconds(structuredSummary.queryTimeoutSec))
                SummaryRow("Лог прогресса", formatRowsInterval(structuredSummary.progressLogEveryRows))
                SummaryRow("Файл merged", structuredSummary.mergedFile ?: "-")
                SummaryRow("Старт", formatDateTime(structuredSummary.startedAt))
                SummaryRow("Завершение", formatDateTime(structuredSummary.finishedAt))
                SummaryRow("Длительность", formatDuration(structuredSummary.startedAt, structuredSummary.finishedAt))
                SummaryRow("Строк в merged", formatNumber(structuredSummary.mergedRowCount))
                SummaryRow("Макс. строк merged", formatNumber(structuredSummary.maxMergedRows))
                SummaryRow("Target включен", formatBooleanFlag(structuredSummary.targetEnabled))
                SummaryRow("Статус target", translateRunStatus(structuredSummary.targetStatus))
                SummaryRow("Таблица target", structuredSummary.targetTable ?: "-")
                SummaryRow("Загружено", formatNumber(structuredSummary.targetRowCount))
                SummaryRow("Ошибка target", structuredSummary.targetErrorMessage ?: "-")
                SummaryRow("Успешных источников", formatNumber(structuredSummary.successfulSourcesCount))
                SummaryRow("Ошибочных источников", formatNumber(structuredSummary.failedSourcesCount))
            }
        }
    }
}

@Composable
internal fun SummaryMetricBadge(
    label: String,
    value: String,
    running: Boolean = false,
) {
    Div({ classes("run-summary-metric") }) {
        Div({ classes("run-summary-metric-label") }) { Text(label) }
        Div({ classes("run-summary-metric-value-wrap") }) {
            Div({ classes("run-summary-metric-value") }) { Text(value) }
            if (running) {
                Div({
                    classes("run-progress-spinner-arrows", "run-summary-spinner-arrows")
                    attr("aria-hidden", "true")
                }) {
                    Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-forward") }) {
                        Text("↻")
                    }
                    Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-backward") }) {
                        Text("↺")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryRow(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-item") }) {
        Div({ classes("run-summary-label") }) { Text(label) }
        Div({ classes("run-summary-value") }) { Text(value) }
    }
}

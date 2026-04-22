package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressMetric
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressWidget
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.component.buildRunProgressStages
import com.sbrf.lt.platform.composeui.foundation.component.eventEntryCssClass
import com.sbrf.lt.platform.composeui.foundation.component.runStatusCssClass
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.foundation.format.statusTone
import com.sbrf.lt.platform.composeui.foundation.run.extractArtifactName
import com.sbrf.lt.platform.composeui.foundation.run.formatBooleanFlag
import com.sbrf.lt.platform.composeui.foundation.run.formatRowsInterval
import com.sbrf.lt.platform.composeui.foundation.run.formatStageDuration
import com.sbrf.lt.platform.composeui.foundation.run.formatTimeoutSeconds
import com.sbrf.lt.platform.composeui.run.StructuredRunSummary
import com.sbrf.lt.platform.composeui.run.artifactStatusTone
import com.sbrf.lt.platform.composeui.run.detectRunStageKey
import com.sbrf.lt.platform.composeui.run.formatFileSizeValue
import com.sbrf.lt.platform.composeui.run.formatPercentValue
import com.sbrf.lt.platform.composeui.run.translateArtifactKind
import com.sbrf.lt.platform.composeui.run.translateArtifactStatus
import com.sbrf.lt.platform.composeui.run.translateLaunchSource
import com.sbrf.lt.platform.composeui.run.translateRunStatus
import com.sbrf.lt.platform.composeui.run.translateStage
import com.sbrf.lt.platform.composeui.run.translateStageKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

private val technicalDiagnosticsJson = Json {
    prettyPrint = true
}

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

@Composable
private fun RunOverviewSection(
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
private fun StructuredSummarySection(structuredSummary: StructuredRunSummary?) {
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
private fun SourceAllocationSection(structuredSummary: StructuredRunSummary?) {
    SectionCard(
        title = "Распределение merged по источникам",
        subtitle = "Сколько строк попало в merged из каждого источника.",
    ) {
        if (structuredSummary == null || structuredSummary.allocations.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) { Text("Нет данных по распределению строк.") }
        } else {
            Div({ classes("table-responsive") }) {
                org.jetbrains.compose.web.dom.Table({ classes("table", "source-status-table", "align-middle", "mb-0") }) {
                    org.jetbrains.compose.web.dom.Thead {
                        org.jetbrains.compose.web.dom.Tr {
                            org.jetbrains.compose.web.dom.Th { Text("Источник") }
                            org.jetbrains.compose.web.dom.Th { Text("Доступно строк") }
                            org.jetbrains.compose.web.dom.Th { Text("Попало в merged") }
                            org.jetbrains.compose.web.dom.Th { Text("Доля") }
                        }
                    }
                    org.jetbrains.compose.web.dom.Tbody {
                        structuredSummary.allocations.forEach { item ->
                            org.jetbrains.compose.web.dom.Tr {
                                org.jetbrains.compose.web.dom.Td { Text(item.sourceName) }
                                org.jetbrains.compose.web.dom.Td { Text(formatNumber(item.availableRows)) }
                                org.jetbrains.compose.web.dom.Td { Text(formatNumber(item.mergedRows)) }
                                org.jetbrains.compose.web.dom.Td { Text(formatPercentValue(item.mergedPercent)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedSourcesSection(structuredSummary: StructuredRunSummary?) {
    SectionCard(
        title = "Проблемные источники",
        subtitle = "Источники с ошибками по данным summary.",
    ) {
        if (structuredSummary == null || structuredSummary.failedSources.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) { Text("Ошибочных источников нет.") }
        } else {
            Div({ classes("table-responsive") }) {
                org.jetbrains.compose.web.dom.Table({ classes("table", "source-status-table", "align-middle", "mb-0") }) {
                    org.jetbrains.compose.web.dom.Thead {
                        org.jetbrains.compose.web.dom.Tr {
                            org.jetbrains.compose.web.dom.Th { Text("Источник") }
                            org.jetbrains.compose.web.dom.Th { Text("Статус") }
                            org.jetbrains.compose.web.dom.Th { Text("Строк") }
                            org.jetbrains.compose.web.dom.Th { Text("Ошибка") }
                        }
                    }
                    org.jetbrains.compose.web.dom.Tbody {
                        structuredSummary.failedSources.forEach { item ->
                            org.jetbrains.compose.web.dom.Tr {
                                org.jetbrains.compose.web.dom.Td { Text(item.sourceName) }
                                org.jetbrains.compose.web.dom.Td {
                                    StatusBadge(
                                        text = translateRunStatus(item.status),
                                        tone = statusTone(item.status),
                                    )
                                }
                                org.jetbrains.compose.web.dom.Td { Text(formatNumber(item.rowCount)) }
                                org.jetbrains.compose.web.dom.Td { Text(item.errorMessage ?: "-") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceResultsSection(details: ModuleRunDetailsResponse) {
    Div({
        attr("id", "run-source-results-section")
    }) {
        SectionCard(
            title = "Результаты по источникам",
            subtitle = "Текущее состояние выгрузки и попадания в merged по каждому источнику.",
        ) {
            if (details.sourceResults.isEmpty()) {
                P({ classes("text-secondary", "mb-0") }) { Text("Данные по источникам пока недоступны.") }
            } else {
                Div({ classes("table-responsive") }) {
                    org.jetbrains.compose.web.dom.Table({ classes("table", "source-status-table", "align-middle", "mb-0") }) {
                        org.jetbrains.compose.web.dom.Thead {
                            org.jetbrains.compose.web.dom.Tr {
                                org.jetbrains.compose.web.dom.Th { Text("Источник") }
                                org.jetbrains.compose.web.dom.Th { Text("Статус") }
                                org.jetbrains.compose.web.dom.Th { Text("Экспортировано") }
                                org.jetbrains.compose.web.dom.Th { Text("Попало в merged") }
                                org.jetbrains.compose.web.dom.Th { Text("Старт") }
                                org.jetbrains.compose.web.dom.Th { Text("Финиш") }
                                org.jetbrains.compose.web.dom.Th { Text("Длительность") }
                                org.jetbrains.compose.web.dom.Th { Text("Ошибка") }
                            }
                        }
                        org.jetbrains.compose.web.dom.Tbody {
                            details.sourceResults.sortedBy { it.sortOrder }.forEach { item ->
                                org.jetbrains.compose.web.dom.Tr {
                                    org.jetbrains.compose.web.dom.Td { Text(item.sourceName) }
                                    org.jetbrains.compose.web.dom.Td {
                                        StatusBadge(
                                            text = translateRunStatus(item.status),
                                            tone = statusTone(item.status),
                                        )
                                    }
                                    org.jetbrains.compose.web.dom.Td { Text(formatNumber(item.exportedRowCount)) }
                                    org.jetbrains.compose.web.dom.Td { Text(formatNumber(item.mergedRowCount)) }
                                    org.jetbrains.compose.web.dom.Td { Text(formatDateTime(item.startedAt)) }
                                    org.jetbrains.compose.web.dom.Td { Text(formatDateTime(item.finishedAt)) }
                                    org.jetbrains.compose.web.dom.Td {
                                        Text(
                                            formatDuration(
                                                item.startedAt,
                                                item.finishedAt,
                                                running = item.status.equals("RUNNING", ignoreCase = true),
                                            ),
                                        )
                                    }
                                    org.jetbrains.compose.web.dom.Td { Text(item.errorMessage ?: "-") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunEventsSection(details: ModuleRunDetailsResponse) {
    SectionCard(
        title = "События",
        subtitle = "Последние события выполнения.",
    ) {
        if (details.events.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) { Text("События пока недоступны.") }
        } else {
            Div {
                details.events.takeLast(60).forEach { event ->
                    Div({ classesFromString(eventEntryCssClass(event.severity)) }) {
                        Div({ classes("human-log-time") }) {
                            Text(
                                listOfNotNull(
                                    formatDateTime(event.timestamp).takeIf { it != "-" },
                                    event.stage?.let(::translateStage),
                                    event.sourceName,
                                ).joinToString(" · ").ifBlank { "-" },
                            )
                        }
                        Div({ classes("human-log-text") }) {
                            Text(event.message ?: event.eventType)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalDiagnosticsSection(
    details: ModuleRunDetailsResponse,
    showTechnicalDiagnostics: Boolean,
    enabled: Boolean,
    onToggleTechnicalDiagnostics: () -> Unit,
) {
    if (!enabled) {
        return
    }
    SectionCard(
        title = "Техническая диагностика",
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                onClick { onToggleTechnicalDiagnostics() }
            }) {
                Text("Показать / скрыть")
            }
        },
    ) {
        if (showTechnicalDiagnostics) {
            Pre({ classes("event-log", "technical-log", "mb-0") }) {
                Text(
                    if (details.events.isEmpty()) {
                        "Технические события пока недоступны."
                    } else {
                        details.events
                            .takeLast(200)
                            .joinToString("\n\n") { event ->
                                technicalDiagnosticsJson.encodeToString(
                                    ModuleRunEventResponse.serializer(),
                                    event,
                                )
                            }
                    },
                )
            }
        } else {
            P({ classes("text-secondary", "mb-0") }) {
                Text("Технические события скрыты.")
            }
        }
    }
}

@Composable
private fun RunArtifactsSection(details: ModuleRunDetailsResponse) {
    Div({
        attr("id", "run-artifacts-section")
    }) {
        SectionCard(
            title = "Результаты запуска",
            subtitle = "Итоговые артефакты выбранного запуска.",
        ) {
            if (details.artifacts.isEmpty()) {
                P({ classes("text-secondary", "mb-0") }) { Text("Результаты запуска пока недоступны.") }
            } else {
                Div({ classes("run-artifact-grid") }) {
                    details.artifacts.forEach { item ->
                        ArtifactCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawSummaryJsonSection(
    details: ModuleRunDetailsResponse,
    history: ModuleRunHistoryResponse,
) {
    Div({
        attr("id", "run-summary-json-section")
    }) {
        SectionCard(
            title = "summary.json",
            subtitle = "Raw-представление итогового summary.",
        ) {
            if (history.uiSettings.showRawSummaryJson) {
                Pre({
                    classes("small", "mb-0", "bg-light", "border", "rounded-3", "p-3")
                }) {
                    Text(details.summaryJson ?: "{}")
                }
            } else {
                P({ classes("text-secondary", "mb-0") }) {
                    Text("Показ raw summary отключен в пользовательских настройках UI.")
                }
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
private fun SummaryRow(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-item") }) {
        Div({ classes("run-summary-label") }) { Text(label) }
        Div({ classes("run-summary-value") }) { Text(value) }
    }
}

@Composable
private fun ArtifactCard(item: ModuleRunArtifactResponse) {
    Div({ classes("run-artifact-card") }) {
        Div({ classes("run-artifact-kind") }) {
            Text(translateArtifactKind(item.artifactKind))
        }
        Div({ classes("run-artifact-title") }) {
            Text(extractArtifactName(item.filePath, item.artifactKey))
        }
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
            StatusBadge(
                text = translateArtifactStatus(item.storageStatus),
                tone = artifactStatusTone(item.storageStatus),
            )
            Span({ classes("run-artifact-note") }) {
                Text(formatFileSizeValue(item.fileSizeBytes))
            }
        }
        Div({ classes("run-artifact-note") }) {
            Text("Ключ: ${item.artifactKey.ifBlank { "-" }}")
        }
        Div({ classes("run-artifact-path") }) {
            Code {
                Text(item.filePath)
            }
        }
    }
}

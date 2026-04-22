package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.component.eventEntryCssClass
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.foundation.format.statusTone
import com.sbrf.lt.platform.composeui.run.StructuredRunSummary
import com.sbrf.lt.platform.composeui.run.formatPercentValue
import com.sbrf.lt.platform.composeui.run.translateRunStatus
import com.sbrf.lt.platform.composeui.run.translateStage
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SourceAllocationSection(structuredSummary: StructuredRunSummary?) {
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
internal fun FailedSourcesSection(structuredSummary: StructuredRunSummary?) {
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
internal fun SourceResultsSection(details: ModuleRunDetailsResponse) {
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
internal fun RunEventsSection(details: ModuleRunDetailsResponse) {
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

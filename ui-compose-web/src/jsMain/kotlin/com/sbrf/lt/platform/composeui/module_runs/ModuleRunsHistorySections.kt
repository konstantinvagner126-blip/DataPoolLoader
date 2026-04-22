package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.statusTone
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.label
import com.sbrf.lt.platform.composeui.run.summarizeSourceCounters
import com.sbrf.lt.platform.composeui.run.translateLaunchSource
import com.sbrf.lt.platform.composeui.run.translateRunStatus
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleRunsOverviewStrip(
    route: ModuleRunsRouteState,
    session: ModuleRunPageSessionResponse?,
    history: ModuleRunHistoryResponse?,
    details: ModuleRunDetailsResponse?,
    state: ModuleRunsPageState,
) {
    val storageLabel = if (route.storage == "database") ModuleStoreMode.DATABASE.label else ModuleStoreMode.FILES.label
    val transportLabel = if (route.storage == "database") "Polling" else "WebSocket"
    val runsCount = history?.runs?.size ?: 0
    val selectedRunLabel = details?.run?.let { "${translateRunStatus(it.status)} · ${it.runId}" }
        ?: history?.activeRunId?.let { "Активный запуск · $it" }
        ?: "Нет активного запуска"

    Div({ classes("runs-overview-grid", "mb-4") }) {
        RunsOverviewCard(
            label = "Хранилище",
            value = storageLabel,
            note = session?.moduleId ?: route.moduleId,
        )
        RunsOverviewCard(
            label = "История на экране",
            value = "$runsCount запусков",
            note = "Лимит ${state.historyLimit}. Фильтр: ${state.historyFilter.label}.",
        )
        RunsOverviewCard(
            label = "Live transport",
            value = transportLabel,
            note = selectedRunLabel,
        )
    }
}

@Composable
private fun RunsOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("runs-overview-card") }) {
        Div({ classes("runs-overview-label") }) { Text(label) }
        Div({ classes("runs-overview-value") }) { Text(value) }
        Div({ classes("runs-overview-note") }) { Text(note) }
    }
}

@Composable
internal fun RunsHistoryPanel(
    state: ModuleRunsPageState,
    runs: List<ModuleRunSummaryResponse>,
    onHistoryLimitChange: (Int) -> Unit,
    onHistoryFilterChange: (ModuleRunsHistoryFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectRun: (String) -> Unit,
) {
    SectionCard(
        title = "История запусков",
        subtitle = "Выбери запуск, чтобы посмотреть подробности.",
        actions = {
            Div({ classes("run-history-toolbar") }) {
                Span({ classes("run-history-control-label") }) {
                    Text("Показывать")
                }
                Select(attrs = {
                    classes("run-history-limit-select")
                    attr("value", state.historyLimit.toString())
                    onChange { event ->
                        event.value?.toIntOrNull()?.let(onHistoryLimitChange)
                    }
                }) {
                    listOf(20, 50, 100).forEach { limit ->
                        Option(value = limit.toString()) {
                            Text(limit.toString())
                        }
                    }
                }
                Span({ classes("run-history-control-label") }) {
                    Text("Поиск")
                }
                Input(type = InputType.Search, attrs = {
                    classes("run-history-search-input")
                    placeholder("runId, модуль, target, output...")
                    value(state.searchQuery)
                    onInput { event ->
                        onSearchQueryChange(event.value)
                    }
                })
            }
        },
    ) {
        Div({ classes("run-history-filters", "mb-3") }) {
            ModuleRunsHistoryFilter.entries.forEach { filter ->
                Button(attrs = {
                    classes("run-history-filter")
                    if (state.historyFilter == filter) {
                        classes("run-history-filter-active")
                    }
                    onClick { onHistoryFilterChange(filter) }
                }) {
                    Text(filter.label)
                }
            }
        }

        Div({ classes("run-history-list") }) {
            runs.forEach { run ->
                Button(attrs = {
                    classes("run-history-item")
                    if (run.runId == state.selectedRunId) {
                        classes("run-history-item-active")
                    }
                    onClick { onSelectRun(run.runId) }
                }) {
                    Div({ classes("run-history-head") }) {
                        Span({ classes("run-history-title") }) {
                            Text(run.moduleTitle.ifBlank { run.moduleId })
                        }
                        StatusBadge(
                            text = translateRunStatus(run.status),
                            tone = statusTone(run.status),
                        )
                    }
                    Div({ classes("run-history-meta") }) {
                        Text(formatDateTime(run.requestedAt ?: run.startedAt))
                    }
                    Div({ classes("run-history-meta") }) {
                        Text("Строк в merged: ${com.sbrf.lt.platform.composeui.foundation.format.formatNumber(run.mergedRowCount)}")
                    }
                    Div({ classes("run-history-meta") }) {
                        Text(summarizeSourceCounters(run))
                    }
                    if (!run.launchSourceKind.isNullOrBlank()) {
                        Div({ classes("run-history-meta") }) {
                            Text("Источник запуска: ${translateLaunchSource(run.launchSourceKind)}")
                        }
                    }
                    if (!run.targetStatus.isNullOrBlank()) {
                        Div({ classes("run-history-meta") }) {
                            Text("Target: ${translateRunStatus(run.targetStatus)}")
                        }
                    }
                }
            }
        }
    }
}

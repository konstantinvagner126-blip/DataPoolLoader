package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SyncRunsHistoryPanel(
    state: ModuleSyncPageState,
    onLimitChange: (Int) -> Unit,
    onSelectRun: (String) -> Unit,
) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("run-history-toolbar") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("История импортов") }
            Label(attrs = { classes("run-history-control-label", "mb-0") }) { Text("Показывать") }
            Select(attrs = {
                classes("run-history-limit-select")
                if (state.actionInProgress != null) {
                    disabled()
                }
                onChange {
                    onLimitChange(it.value?.toIntOrNull() ?: state.historyLimit)
                }
            }) {
                listOf(10, 20, 50).forEach { limit ->
                    Option(value = limit.toString(), attrs = {
                        if (state.historyLimit == limit) {
                            selected()
                        }
                    }) {
                        Text(limit.toString())
                    }
                }
            }
        }

        Div({ classes("mt-3") }) {
            when {
                state.loading && state.runs.isEmpty() -> {
                    LoadingStateCard("История импортов", "Загружаю последние синхронизации.")
                }

                state.runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE -> {
                    EmptyStateCard("История импортов", "История станет доступна после переключения в режим «База данных».")
                }

                state.runs.isEmpty() -> {
                    EmptyStateCard("История импортов", "Импорты пока не запускались.")
                }

                else -> {
                    state.runs.forEach { run ->
                        Button(attrs = {
                            classes("run-history-item")
                            if (run.syncRunId == state.selectedRunId) {
                                classes("run-history-item-active")
                            }
                            attr("type", "button")
                            onClick { onSelectRun(run.syncRunId) }
                        }) {
                            Div({ classes("run-history-head") }) {
                                Span({ classes("run-history-title") }) { Text(syncRunTitle(run)) }
                                StatusBadge(
                                    text = translateSyncStatus(run.status),
                                    tone = syncStatusTone(run.status),
                                )
                            }
                            Div({ classes("run-history-meta") }) { Text(formatDateTime(run.startedAt)) }
                            Div({ classes("run-history-meta") }) {
                                Text(
                                    "Обработано: ${run.totalProcessed} · создано: ${run.totalCreated} · " +
                                        "пропущено: ${run.totalSkipped} · ошибок: ${run.totalFailed}",
                                )
                            }
                            Div({ classes("run-history-meta") }) {
                                Text(syncRunMeta(run, ::formatDateTime))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SyncRunDetailsPanel(state: ModuleSyncPageState) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("panel-title") }) { Text("Детали импорта") }

        Div({ classes("mt-3") }) {
            val details = state.selectedRunDetails
            if (details == null) {
                EmptyStateCard("Детали импорта", "Выбери запуск из истории слева, чтобы посмотреть детали.")
                return@Div
            }

            val run = details.run
            Div({ classes("run-summary-header") }) {
                Div {
                    Div({ classes("run-summary-title") }) {
                        Text(syncRunTitle(run))
                    }
                    Div({ classes("run-summary-subtitle") }) {
                        Text("${translateSyncStatus(run.status)} · ${formatDateTime(run.startedAt)}")
                    }
                }
                Div({ classes("run-summary-metrics") }) {
                    SyncMetricBadge("Область", translateSyncScope(run.scope))
                    SyncMetricBadge("Обработано", run.totalProcessed.toString())
                    SyncMetricBadge("Создано", run.totalCreated.toString())
                    SyncMetricBadge("Обновлено", run.totalUpdated.toString())
                    SyncMetricBadge("Пропущено", run.totalSkipped.toString())
                    SyncMetricBadge("С ошибкой", run.totalFailed.toString())
                }
            }

            Div({ classes("run-summary-list") }) {
                SyncSummaryRow("Запуск", syncRunTitle(run))
                SyncSummaryRow("Инициатор", actorLabel(run.startedByActorDisplayName, run.startedByActorId) ?: "-")
                SyncSummaryRow("Старт", formatDateTime(run.startedAt))
                SyncSummaryRow("Завершение", run.finishedAt?.let(::formatDateTime) ?: "еще выполняется")
                SyncSummaryRow("Идентификатор", run.syncRunId)
            }

            if (details.items.isEmpty()) {
                Div({ classes("text-secondary", "small", "mt-3") }) {
                    Text("Детали по модулям появятся после завершения импорта.")
                }
                return@Div
            }

            Div({ classes("mt-3") }) {
                details.items.forEach { item ->
                    Div({ classes("card", "mb-3") }) {
                        Div({ classes("card-body", "py-3", "px-3") }) {
                            Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
                                Code({ classes("fw-semibold") }) { Text(item.moduleCode) }
                                StatusBadge(
                                    text = translateSyncAction(item.action),
                                    tone = syncActionTone(item.action),
                                )
                                StatusBadge(
                                    text = translateSyncStatus(item.status),
                                    tone = syncStatusTone(item.status),
                                )
                                if (!item.resultRevisionId.isNullOrBlank()) {
                                    Span({ classes("small", "text-secondary") }) { Text("Ревизия: ${item.resultRevisionId}") }
                                }
                            }
                            val errorMessage = item.errorMessage
                            if (!errorMessage.isNullOrBlank()) {
                                Div({ classes("small", "text-danger", "mt-2") }) { Text(errorMessage) }
                            }
                            val itemDetails = item.details
                            val reason = itemDetails?.get("reason")?.toString()?.trim('"')
                            val message = itemDetails?.get("message")?.toString()?.trim('"')
                            if (!reason.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary", "mt-2") }) { Text("Причина: $reason") }
                            }
                            if (!message.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary") }) { Text(message) }
                            }
                            if (itemDetails != null && itemDetails.isNotEmpty()) {
                                Pre({ classes("bg-body-tertiary", "rounded", "p-2", "mt-2", "small", "mb-0") }) {
                                    Text(itemDetails.toString())
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
internal fun SyncMetricBadge(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-metric") }) {
        Div({ classes("run-summary-metric-label") }) { Text(label) }
        Div({ classes("run-summary-metric-value") }) { Text(value) }
    }
}

@Composable
internal fun SyncSummaryRow(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-item") }) {
        Div({ classes("run-summary-label") }) { Text(label) }
        Div({ classes("run-summary-value") }) { Text(value) }
    }
}

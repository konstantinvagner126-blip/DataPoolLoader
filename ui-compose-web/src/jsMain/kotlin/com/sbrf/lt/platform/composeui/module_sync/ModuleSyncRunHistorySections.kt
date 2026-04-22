package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
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

package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun RuntimeAlert(state: ModuleSyncPageState) {
    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    when {
        runtimeContext == null -> return
        runtimeContext.effectiveMode != ModuleStoreMode.DATABASE -> {
            AlertBanner(
                buildDatabaseModeUnavailableMessage(
                    runtimeContext.fallbackReason,
                    "Для импорта нужен активный режим «База данных».",
                ),
                "warning",
            )
        }

        syncState?.maintenanceMode == true -> {
            AlertBanner(buildMaintenanceMessage(syncState), "warning")
        }

        syncState?.activeSingleSyncs?.isNotEmpty() == true -> {
            AlertBanner(
                syncState.activeSingleSyncs.joinToString(" ") { describeActiveSingleSync(it) },
                "info",
            )
        }
    }
}

@Composable
internal fun SyncActionsPanel(
    state: ModuleSyncPageState,
    onToggleSelectiveSync: () -> Unit,
    onSyncAll: () -> Unit,
    onSyncSelected: () -> Unit,
) {
    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    val databaseModeActive = runtimeContext?.effectiveMode == ModuleStoreMode.DATABASE
    val maintenanceMode = syncState?.maintenanceMode == true
    val hasSelection = state.selectedModuleCodes.isNotEmpty()
    val canSyncAll = databaseModeActive && !maintenanceMode && state.actionInProgress == null
    val canSyncSelected = databaseModeActive && !maintenanceMode && state.actionInProgress == null && hasSelection

    Div({ classes("module-sync-toolbar", "mb-4") }) {
        Div({ classes("module-sync-toolbar-row") }) {
            Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-primary") }) {
                Div({ classes("module-editor-toolbar-group-label") }) { Text("Массовый импорт") }
                Button(attrs = {
                    classes("btn", "btn-primary")
                    attr("type", "button")
                    if (!canSyncAll) {
                        disabled()
                    }
                    onClick { onSyncAll() }
                }) {
                    Text(if (state.actionInProgress == "sync-all") "Синхронизация..." else "Синхронизировать все модули")
                }
            }
            Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                Div({ classes("module-editor-toolbar-group-label") }) { Text("Выборочная синхронизация") }
                Button(attrs = {
                    classes("btn", "btn-outline-primary")
                    attr("type", "button")
                    if (!databaseModeActive || maintenanceMode || state.actionInProgress != null) {
                        disabled()
                    }
                    onClick { onToggleSelectiveSync() }
                }) {
                    Text(if (state.selectiveSyncVisible) "Скрыть список модулей" else "Выбрать модули")
                }
                Button(attrs = {
                    classes("btn", "btn-primary")
                    attr("type", "button")
                    if (!canSyncSelected) {
                        disabled()
                    }
                    onClick { onSyncSelected() }
                }) {
                    Text(
                        when {
                            state.actionInProgress == "sync-selected" -> "Синхронизация..."
                            state.selectedModuleCodes.size <= 1 -> "Синхронизировать выбранный"
                            else -> "Синхронизировать выбранные"
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SyncOverviewPanel(state: ModuleSyncPageState) {
    val syncState = state.syncState
    val syncLabel = when {
        syncState?.maintenanceMode == true -> "Массовая синхронизация"
        !syncState?.activeSingleSyncs.isNullOrEmpty() -> "Точечный импорт"
        else -> "Ожидание"
    }
    val syncNote = when {
        syncState?.maintenanceMode == true -> buildMaintenanceMessage(syncState)
        !syncState?.activeSingleSyncs.isNullOrEmpty() -> syncState.activeSingleSyncs.joinToString(" ") { describeActiveSingleSync(it) }
        else -> syncState?.message?.takeIf { it.isNotBlank() } ?: "Новых операций синхронизации сейчас нет."
    }

    Div({ classes("sync-overview-grid", "mb-4") }) {
        SyncOverviewCard(
            label = "Состояние импорта",
            value = syncLabel,
            note = syncNote,
        )
    }
}

@Composable
internal fun SyncOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("sync-overview-card") }) {
        Div({ classes("sync-overview-label") }) { Text(label) }
        Div({ classes("sync-overview-value") }) { Text(value) }
        Div({ classes("sync-overview-note") }) { Text(note) }
    }
}

@Composable
internal fun SelectiveModulesPanel(
    state: ModuleSyncPageState,
    onSearchQueryChange: (String) -> Unit,
    onToggleModule: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSyncSelected: () -> Unit,
) {
    val filteredModules = filterSelectableModules(state)
    val activeSingleSyncCodes = state.syncState?.activeSingleSyncs
        ?.mapNotNull { it.moduleCode }
        ?.toSet()
        .orEmpty()

    Div({ classes("panel", "h-100") }) {
        Div({ classes("run-history-toolbar") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("Выборочная синхронизация") }
            Input(type = InputType.Search, attrs = {
                classes("run-history-search-input")
                value(state.moduleSearchQuery)
                attr("placeholder", "Поиск по коду или названию...")
                onInput { onSearchQueryChange(it.value) }
            })
        }

        Div({ classes("module-sync-selection-toolbar", "mt-3") }) {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (filteredModules.isEmpty()) {
                    disabled()
                }
                onClick { onSelectAll() }
            }) { Text("Выбрать все") }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (state.selectedModuleCodes.isEmpty()) {
                    disabled()
                }
                onClick { onClearSelection() }
            }) { Text("Снять все") }
            Button(attrs = {
                classes("btn", "btn-primary", "btn-sm")
                attr("type", "button")
                if (state.selectedModuleCodes.isEmpty() || state.actionInProgress != null) {
                    disabled()
                }
                onClick { onSyncSelected() }
            }) {
                Text(
                    when {
                        state.actionInProgress == "sync-selected" -> "Синхронизация..."
                        state.selectedModuleCodes.size <= 1 -> "Синхронизировать выбранный"
                        else -> "Синхронизировать ${state.selectedModuleCodes.size} модуля"
                    },
                )
            }
        }

        Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
            Text("Отметь файловые модули, которые нужно импортировать в базу данных.")
        }

        when {
            state.runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE -> {
                EmptyStateCard("Выборочная синхронизация", "Список модулей станет доступен после переключения в режим «База данных».")
            }

            filteredModules.isEmpty() -> {
                EmptyStateCard("Выборочная синхронизация", "Подходящих файловых модулей не найдено.")
            }

            else -> {
                Div({ classes("module-sync-module-list") }) {
                    filteredModules.forEach { module ->
                        val isSelected = module.id in state.selectedModuleCodes
                        val isRunning = module.id in activeSingleSyncCodes
                        Label(attrs = {
                            classes("module-sync-module-card")
                            if (isSelected) {
                                classes("module-sync-module-card-selected")
                            }
                        }) {
                            Input(type = InputType.Checkbox, attrs = {
                                if (isSelected) {
                                    attr("checked", "checked")
                                }
                                onChange { onToggleModule(module.id) }
                            })
                            Div({ classes("module-sync-module-card-copy") }) {
                                Div({ classes("module-sync-module-card-head") }) {
                                    Span({ classes("module-sync-module-card-title") }) {
                                        Text(module.title.ifBlank { module.id })
                                    }
                                    if (isRunning) {
                                        Span({ classes("badge", "text-bg-info") }) {
                                            Text("Идет импорт")
                                        }
                                    }
                                }
                                Div({ classes("module-sync-module-card-code") }) {
                                    Text(module.id)
                                }
                                val description = module.description
                                if (!description.isNullOrBlank()) {
                                    Div({ classes("module-sync-module-card-description") }) {
                                        Text(description)
                                    }
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
                                Span({ classes("badge", *syncStatusBadgeClass(run.status).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                                    Text(translateSyncStatus(run.status))
                                }
                            }
                            Div({ classes("run-history-meta") }) { Text(formatInstant(run.startedAt)) }
                            Div({ classes("run-history-meta") }) {
                                Text(
                                    "Обработано: ${run.totalProcessed} · создано: ${run.totalCreated} · " +
                                        "пропущено: ${run.totalSkipped} · ошибок: ${run.totalFailed}",
                                )
                            }
                            Div({ classes("run-history-meta") }) {
                                Text(syncRunMeta(run))
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
                        Text("${translateSyncStatus(run.status)} · ${formatInstant(run.startedAt)}")
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
                SyncSummaryRow("Инициатор", run.startedByActorDisplayName ?: run.startedByActorId ?: "-")
                SyncSummaryRow("Старт", formatInstant(run.startedAt))
                SyncSummaryRow("Завершение", run.finishedAt?.let(::formatInstant) ?: "еще выполняется")
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
                                Span({ classes("badge", *syncActionBadgeClass(item.action).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                                    Text(translateSyncAction(item.action))
                                }
                                Span({ classes("badge", *syncStatusBadgeClass(item.status).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                                    Text(translateSyncStatus(item.status))
                                }
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

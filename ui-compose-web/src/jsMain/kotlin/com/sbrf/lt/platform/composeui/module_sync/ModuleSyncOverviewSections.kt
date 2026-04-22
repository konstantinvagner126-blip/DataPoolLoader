package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
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
            AlertBanner(buildMaintenanceMessage(syncState, ::formatDateTime), "warning")
        }

        syncState?.activeSingleSyncs?.isNotEmpty() == true -> {
            AlertBanner(
                buildActiveSingleSyncSummary(syncState.activeSingleSyncs, ::formatDateTime),
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
        syncState?.maintenanceMode == true -> buildMaintenanceMessage(syncState, ::formatDateTime)
        !syncState?.activeSingleSyncs.isNullOrEmpty() -> buildActiveSingleSyncSummary(syncState.activeSingleSyncs, ::formatDateTime)
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

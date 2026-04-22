package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

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

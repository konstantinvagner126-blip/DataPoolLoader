package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeModuleSyncPage(
    api: ModuleSyncApi = remember { ModuleSyncApiClient() },
) {
    val store = remember(api) { ModuleSyncStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ModuleSyncPageState()) }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = store.load()
    }

    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    val databaseModeActive = runtimeContext?.effectiveMode == ModuleStoreMode.DATABASE
    val hasActiveSync = syncState?.maintenanceMode == true || syncState?.activeSingleSyncs?.isNotEmpty() == true

    PollingEffect(
        enabled = !state.loading && (databaseModeActive || hasActiveSync),
        intervalMs = 5000,
        onTick = {
            state = store.refresh(state)
        },
    )

    PageScaffold(
        eyebrow = "Режим базы данных",
        title = "Импорт модулей из файлов",
        subtitle = "Синхронизация файловых модулей из каталога apps в базу данных. Создание модулей на основе application.yml и SQL-ресурсов.",
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("Импорт из файлов") }
            }
        },
        content = {
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }

            RuntimeAlert(state)

            Div({ classes("panel") }) {
                Div({ classes("panel-title") }) { Text("Импорт модулей") }
                P({ classes("text-secondary", "small", "mt-2") }) {
                    Text(
                        "Импорт просматривает каталог apps, находит модули с application.yml и создает соответствующие записи в базе данных. " +
                            "Модули с тем же составом конфигурации и SQL-ресурсов пропускаются.",
                    )
                }

                SyncOverviewPanel(state)

                SyncActionsPanel(
                    state = state,
                    onToggleSelectiveSync = { state = store.toggleSelectiveSync(state) },
                    onSyncAll = {
                        if (!window.confirm("Синхронизировать все файловые модули в базу данных?")) {
                            return@SyncActionsPanel
                        }
                        scope.launch {
                            state = store.beginAction(state, "sync-all")
                            state = store.syncAll(state)
                        }
                    },
                    onSyncSelected = {
                        scope.launch {
                            state = store.beginAction(state, "sync-selected")
                            state = store.syncSelected(state)
                        }
                    },
                )

                Div({ classes("row", "g-4", "mt-1") }) {
                    Div({ classes("col-12", "col-lg-4") }) {
                        if (state.selectiveSyncVisible) {
                            SelectiveModulesPanel(
                                state = state,
                                onSearchQueryChange = { state = store.updateModuleSearchQuery(state, it) },
                                onToggleModule = { moduleCode -> state = store.toggleModuleSelection(state, moduleCode) },
                                onSelectAll = {
                                    state = store.selectAllModules(
                                        state,
                                        filterSelectableModules(state).map { it.id },
                                    )
                                },
                                onClearSelection = { state = store.clearSelectedModules(state) },
                                onSyncSelected = {
                                    scope.launch {
                                        state = store.beginAction(state, "sync-selected")
                                        state = store.syncSelected(state)
                                    }
                                },
                            )
                        } else {
                            SyncRunsHistoryPanel(
                                state = state,
                                onLimitChange = { limit ->
                                    scope.launch {
                                        val nextState = store.updateHistoryLimit(state, limit)
                                        state = store.startLoading(nextState)
                                        state = store.load(
                                            historyLimit = limit,
                                            preferredRunId = nextState.selectedRunId,
                                            selectiveSyncVisible = nextState.selectiveSyncVisible,
                                            selectedModuleCodes = nextState.selectedModuleCodes,
                                            moduleSearchQuery = nextState.moduleSearchQuery,
                                        )
                                    }
                                },
                                onSelectRun = { syncRunId ->
                                    scope.launch {
                                        state = store.selectRun(state, syncRunId)
                                    }
                                },
                            )
                        }
                    }
                    Div({ classes("col-12", "col-lg-8") }) {
                        SyncRunDetailsPanel(state)
                    }
                }
            }
        },
    )
}

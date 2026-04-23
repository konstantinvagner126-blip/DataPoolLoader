package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleSyncPageContent(
    state: ModuleSyncPageState,
    callbacks: ModuleSyncPageCallbacks,
) {
    Div({ classes("module-sync-content-shell") }) {
        state.errorMessage?.let { AlertBanner(it, "warning") }
        state.successMessage?.let { AlertBanner(it, "success") }

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
                onToggleSelectiveSync = callbacks.onToggleSelectiveSync,
                onSyncAll = callbacks.onSyncAll,
                onSyncSelected = callbacks.onSyncSelected,
            )

            Div({ classes("row", "g-4", "mt-1") }) {
                Div({ classes("col-12", "col-lg-4") }) {
                    if (state.selectiveSyncVisible) {
                        SelectiveModulesPanel(
                            state = state,
                            onSearchQueryChange = callbacks.onSearchQueryChange,
                            onToggleModule = callbacks.onToggleModule,
                            onSelectAll = callbacks.onSelectAll,
                            onClearSelection = callbacks.onClearSelection,
                            onSyncSelected = callbacks.onSyncSelected,
                        )
                    } else {
                        SyncRunsHistoryPanel(
                            state = state,
                            onLimitChange = callbacks.onHistoryLimitChange,
                            onSelectRun = callbacks.onSelectRun,
                        )
                    }
                }
                Div({ classes("col-12", "col-lg-8") }) {
                    SyncRunDetailsPanel(state)
                }
            }
        }
    }
}

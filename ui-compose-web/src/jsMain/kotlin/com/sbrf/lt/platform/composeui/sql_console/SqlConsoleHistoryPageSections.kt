package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleHistoryPageContent(
    state: SqlConsolePageState,
    workspaceId: String,
    onApplyExecutionHistory: (SqlConsoleExecutionHistoryEntry) -> Unit,
    onRepeatExecutionHistory: (SqlConsoleExecutionHistoryEntry) -> Unit,
) {
    Div({ classes("sql-history-content-shell") }) {
        state.errorMessage?.let { AlertBanner(it, "warning") }
        state.successMessage?.let { AlertBanner(it, "success") }
        state.runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
            AlertBanner(
                buildRuntimeModeFallbackMessage(
                    fallbackContext,
                    suffix = "History screen доступен, но runtime screens модулей работают по текущему runtime-context.",
                ),
                "warning",
            )
        }

        if (state.loading && state.info == null) {
            LoadingStateCard(
                title = "История запусков SQL-консоли",
                text = "History текущего workspace загружается.",
            )
        } else {
            Div({ classes("sql-history-screen-head") }) {
                Div({ classes("sql-history-screen-summary") }) {
                    Div({ classes("panel-title", "mb-1") }) { Text("История текущей вкладки") }
                    Div({ classes("small", "text-secondary") }) {
                        Text("Workspace-scoped execution log. Подставить и Повторить работают только для этого workspace.")
                    }
                }
                Div({ classes("sql-query-library-summary-chips") }) {
                    SqlQueryLibrarySummaryChip("Workspace", workspaceId.takeLast(10))
                    SqlQueryLibrarySummaryChip("Sessions", state.executionHistory.size.toString())
                }
            }

            if (state.executionHistory.isEmpty()) {
                EmptyStateCard(
                    title = "История пока пуста",
                    text = "Для этого workspace еще не было execution session. Вернись в SQL-консоль и выполни запрос.",
                )
            } else {
                Div({ classes("panel", "sql-history-screen-panel") }) {
                    SqlConsoleExecutionHistoryList(
                        history = state.executionHistory,
                        onApply = onApplyExecutionHistory,
                        onRepeat = onRepeatExecutionHistory,
                    )
                }
            }
        }
    }
}

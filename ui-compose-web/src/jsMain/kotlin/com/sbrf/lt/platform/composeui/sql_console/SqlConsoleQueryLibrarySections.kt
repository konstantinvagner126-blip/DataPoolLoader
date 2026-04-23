package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun QueryLibraryBlock(
    state: SqlConsolePageState,
    selectedSqlText: String,
    selectedSqlLineCount: Int,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    currentOutlineItem: SqlScriptOutlineItem?,
    runButtonClass: String,
    pendingManualTransaction: Boolean,
    isRunning: Boolean,
    exportableResult: SqlConsoleQueryResult?,
    activeExportShard: String?,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onOpenExecutionHistory: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onFormatSql: () -> Unit,
    onOpenNewTab: () -> Unit,
    onExplainCurrent: () -> Unit,
    onExplainAnalyzeCurrent: () -> Unit,
    onExplainSelection: () -> Unit,
    onExplainAnalyzeSelection: () -> Unit,
    onRunCurrent: () -> Unit,
    onRunSelection: () -> Unit,
    onRunAll: () -> Unit,
    onStop: () -> Unit,
    onCommit: () -> Unit,
    onRollback: () -> Unit,
    onExportCsv: () -> Unit,
    onExportZip: () -> Unit,
) {
    Div({ classes("sql-tool-window", "sql-query-library", "mb-3") }) {
        SqlConsoleQueryLibrarySummary(
            state = state,
            selectedRecentQuery = selectedRecentQuery,
            selectedFavoriteQuery = selectedFavoriteQuery,
            onOpenExecutionHistory = onOpenExecutionHistory,
        )
        Div({ classes("sql-query-library-row") }) {
            SqlConsoleQueryPickerBlock(
                fieldId = "composeRecentQueries",
                label = "Последние запросы",
                queries = state.recentQueries,
                selectedQuery = selectedRecentQuery,
                emptyText = "История пока пуста",
                selectText = "Выбери запрос",
                onSelected = onRecentSelected,
                onApply = onApplyRecent,
            ) {
                SqlLibraryActionButton("Очистить", "btn-outline-secondary") { onClearRecent() }
            }
            SqlConsoleQueryPickerBlock(
                fieldId = "composeFavoriteQueries",
                label = "Избранные запросы",
                queries = state.favoriteQueries,
                selectedQuery = selectedFavoriteQuery,
                emptyText = "Избранное пока пусто",
                selectText = "Выбери запрос",
                onSelected = onFavoriteSelected,
                onApply = onApplyFavorite,
            ) {
                SqlLibraryActionButton("В избранное", "btn-outline-primary") { onRememberFavorite() }
                SqlLibraryActionButton(
                    "Убрать",
                    "btn-outline-danger",
                    disabled = selectedFavoriteQuery.isBlank(),
                ) { onRemoveFavorite() }
            }
        }
        SqlConsoleQuerySettingsBlock(
            state = state,
            onStrictSafetyToggle = onStrictSafetyToggle,
            onAutoCommitToggle = onAutoCommitToggle,
        )
        SqlConsoleWorkspaceToolbar(
            state = state,
            selectedSqlText = selectedSqlText,
            selectedSqlLineCount = selectedSqlLineCount,
            currentOutlineItem = currentOutlineItem,
            runButtonClass = runButtonClass,
            pendingManualTransaction = pendingManualTransaction,
            isRunning = isRunning,
            exportableResult = exportableResult,
            activeExportShard = activeExportShard,
            onPageSizeChange = onPageSizeChange,
            onFormatSql = onFormatSql,
            onOpenNewTab = onOpenNewTab,
            onExplainCurrent = onExplainCurrent,
            onExplainAnalyzeCurrent = onExplainAnalyzeCurrent,
            onExplainSelection = onExplainSelection,
            onExplainAnalyzeSelection = onExplainAnalyzeSelection,
            onRunCurrent = onRunCurrent,
            onRunSelection = onRunSelection,
            onRunAll = onRunAll,
            onStop = onStop,
            onCommit = onCommit,
            onRollback = onRollback,
            onExportCsv = onExportCsv,
            onExportZip = onExportZip,
        )
    }
}

@Composable
private fun SqlConsoleQueryLibrarySummary(
    state: SqlConsolePageState,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    onOpenExecutionHistory: () -> Unit,
) {
    Div({ classes("sql-query-library-summary") }) {
        Div({ classes("sql-tool-window-head") }) {
            Div({ classes("sql-tool-window-heading") }) {
                Div({ classes("eyebrow", "mb-1") }) { Text("Tool Window") }
                Div({ classes("panel-title", "mb-1") }) { Text("Шаблоны и быстрые действия") }
                Div({ classes("small", "text-secondary", "sql-tool-window-note") }) {
                    Text("Выбери готовый запрос, подставь его в редактор или закрепи текущий SQL без переключения контекста.")
                }
            }
            Div({ classes("sql-tool-window-actions", "sql-query-library-summary-chips") }) {
                SqlQueryLibrarySummaryChip("История", state.recentQueries.size.toString())
                SqlQueryLibrarySummaryChip("Избранное", state.favoriteQueries.size.toString())
                SqlQueryLibrarySummaryChip("Объекты", state.favoriteObjects.size.toString())
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm", "sql-tool-window-open-button")
                    attr("type", "button")
                    onClick { onOpenExecutionHistory() }
                }) {
                    Text("История запусков")
                }
            }
        }
        buildSelectedQuerySummary(
            selectedRecentQuery = selectedRecentQuery,
            selectedFavoriteQuery = selectedFavoriteQuery,
        )?.let { summary ->
            Div({ classes("sql-query-library-selection-note") }) {
                Text(summary)
            }
        }
    }
}

@Composable
internal fun SqlQueryLibrarySummaryChip(
    label: String,
    value: String,
) {
    Div({ classes("sql-query-library-summary-chip") }) {
        Div({ classes("sql-query-library-summary-chip-label") }) { Text(label) }
        Div({ classes("sql-query-library-summary-chip-value") }) { Text(value) }
    }
}

private fun buildSelectedQuerySummary(
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
): String? =
    when {
        selectedFavoriteQuery.isNotBlank() -> "Выбран избранный запрос. Следующий шаг: подставить его в редактор или убрать из списка."
        selectedRecentQuery.isNotBlank() -> "Выбран запрос из истории. Следующий шаг: подставить его в редактор и при необходимости сохранить в избранное."
        else -> null
    }

@Composable
internal fun SqlLibraryActionButton(
    label: String,
    toneClass: String,
    disabled: Boolean = false,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "btn-sm")
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

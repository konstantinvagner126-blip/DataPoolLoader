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
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onOpenExecutionHistory: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
) {
    Div({ classes("sql-tool-window", "sql-tool-window-secondary", "sql-query-library", "mt-3") }) {
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
                Div({ classes("panel-title", "mb-0") }) { Text("Библиотека запросов") }
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
        selectedFavoriteQuery.isNotBlank() -> "Выбран избранный запрос."
        selectedRecentQuery.isNotBlank() -> "Выбран запрос из истории."
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

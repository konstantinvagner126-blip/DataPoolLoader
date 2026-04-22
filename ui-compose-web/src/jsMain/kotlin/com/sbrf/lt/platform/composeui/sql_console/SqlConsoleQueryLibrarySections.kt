package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
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
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
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
        SqlConsoleSettingToggle(
            label = "Read-only",
            checked = state.strictSafetyEnabled,
            onToggle = onStrictSafetyToggle,
        )
        SqlConsoleSettingToggle(
            label = "Autocommit",
            checked = state.transactionMode == "AUTO_COMMIT",
            onToggle = { onAutoCommitToggle(state.transactionMode != "AUTO_COMMIT") },
        )
    }
}

@Composable
private fun SqlConsoleQueryPickerBlock(
    fieldId: String,
    label: String,
    queries: List<String>,
    selectedQuery: String,
    emptyText: String,
    selectText: String,
    onSelected: (String) -> Unit,
    onApply: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Div({ classes("sql-query-library-block") }) {
        Label(attrs = {
            classes("small", "text-secondary", "mb-1")
            attr("for", fieldId)
        }) { Text(label) }
        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
            Select(attrs = {
                id(fieldId)
                classes("form-select", "form-select-sm", "sql-recent-query-select")
                onChange { onSelected(it.value ?: "") }
            }) {
                Option(value = "") { Text(if (queries.isEmpty()) emptyText else selectText) }
                queries.forEach { query ->
                    Option(value = query, attrs = { if (selectedQuery == query) selected() }) {
                        Text(query.take(120))
                    }
                }
            }
            SqlLibraryActionButton(
                "Подставить",
                "btn-outline-secondary",
                disabled = selectedQuery.isBlank(),
            ) { onApply() }
            actions()
        }
    }
}

@Composable
private fun SqlConsoleSettingToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Div({ classes("sql-query-library-block") }) {
        Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
            Input(type = InputType.Checkbox, attrs = {
                classes("form-check-input")
                if (checked) {
                    attr("checked", "checked")
                }
                onClick { onToggle() }
            })
            Span { Text(label) }
        }
    }
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

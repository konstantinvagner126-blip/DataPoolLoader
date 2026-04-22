package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleQueryPickerBlock(
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

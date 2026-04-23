package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleSourceSelectionBlock(
    sourceGroups: List<SqlConsoleSourceGroup>,
    sourceNames: List<String>,
    selectedSourceNames: List<String>,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    onToggleSourceGroup: (SqlConsoleSourceGroup, Boolean) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
) {
    Div({ classes("mt-3", "sql-source-selection") }) {
        if (sourceGroups.isNotEmpty()) {
            Div({ classes("sql-source-group-selection") }) {
                Div({ classes("sql-source-selection-caption") }) {
                    Text("Группы")
                }
                sourceGroups.forEach { sourceGroup ->
                    val selectionState = sourceGroupSelectionState(sourceGroup, selectedSourceNames)
                    SqlConsoleSourceGroupCheckbox(
                        group = sourceGroup,
                        selectionState = selectionState,
                        onToggle = {
                            onToggleSourceGroup(sourceGroup, selectionState != SqlConsoleSourceGroupSelectionState.ALL)
                        },
                    )
                }
            }
        }
        if (sourceNames.isNotEmpty()) {
            Div({ classes("sql-source-selection-caption") }) {
                Text("Источники")
            }
        }
        sourceNames.forEach { sourceName ->
            val sourceStatus = connectionStatusBySource[sourceName]
            val selected = sourceName in selectedSourceNames
            SqlConsoleSourceCheckbox(
                sourceName = sourceName,
                sourceStatus = sourceStatus,
                selected = selected,
                onToggle = { onToggleSource(sourceName, !selected) },
            )
        }
    }
}

@Composable
internal fun SqlConsoleSourceGroupCheckbox(
    group: SqlConsoleSourceGroup,
    selectionState: SqlConsoleSourceGroupSelectionState,
    onToggle: () -> Unit,
) {
    val selected = selectionState == SqlConsoleSourceGroupSelectionState.ALL
    Label(attrs = {
        classes("sql-source-group-checkbox")
        if (selected) {
            classes("sql-source-group-checkbox-selected")
        }
        if (selectionState == SqlConsoleSourceGroupSelectionState.PARTIAL) {
            classes("sql-source-group-checkbox-partial")
        }
    }) {
        Input(type = InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            onClick { onToggle() }
        })
        Div({ classes("sql-source-group-checkbox-body") }) {
            Div({ classes("sql-source-group-checkbox-head") }) {
                Span({ classes("sql-source-group-checkbox-name") }) {
                    Text(group.name)
                }
                Span({ classes("sql-source-group-checkbox-status") }) {
                    Text(translateSourceGroupSelectionState(selectionState))
                }
            }
            Div({ classes("sql-source-group-checkbox-message") }) {
                Text("Источников в группе: ${group.sourceNames.size}. Выбор группы добавляет или снимает все ее source.")
            }
        }
    }
}

@Composable
internal fun SqlConsoleSourceCheckbox(
    sourceName: String,
    sourceStatus: SqlConsoleSourceConnectionStatus?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("sql-source-checkbox", "sql-source-checkbox-${sourceStatusTone(sourceStatus)}")
        if (selected) {
            classes("sql-source-checkbox-selected")
        }
    }) {
        Input(type = InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            onClick { onToggle() }
        })
        Div({ classes("sql-source-checkbox-body") }) {
            Div({ classes("sql-source-checkbox-head") }) {
                Span({ classes("sql-source-checkbox-name") }) {
                    Text(sourceName)
                }
                Span({ classes("sql-source-checkbox-status") }) {
                    Text(sourceStatus?.let { translateConnectionStatus(it.status) } ?: "Не проверено")
                }
            }
            Div({ classes("sql-source-checkbox-message") }) {
                Text(
                    sourceStatus?.errorMessage
                        ?: sourceStatus?.message
                        ?: "Статус появится после проверки подключения или выполнения SQL по этому source.",
                )
            }
        }
    }
}

private fun translateSourceGroupSelectionState(
    selectionState: SqlConsoleSourceGroupSelectionState,
): String =
    when (selectionState) {
        SqlConsoleSourceGroupSelectionState.ALL -> "Все выбраны"
        SqlConsoleSourceGroupSelectionState.PARTIAL -> "Частично"
        SqlConsoleSourceGroupSelectionState.NONE -> "Не выбрана"
    }

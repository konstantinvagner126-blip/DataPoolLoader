package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleSourceSelectionBlock(
    groups: List<SqlConsoleSourceGroup>,
    sourceCatalogNames: List<String>,
    selectedSourceNames: List<String>,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    onToggleSourceGroup: (SqlConsoleSourceGroup, Boolean) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
) {
    val displayGroups = groups.ifEmpty {
        sourceCatalogNames
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(SqlConsoleSourceGroup(name = "Без группы", sources = it, synthetic = true)) }
            .orEmpty()
    }
    Div({ classes("mt-3", "sql-source-selection") }) {
        if (displayGroups.isEmpty()) {
            Div({ classes("small", "text-secondary") }) {
                Text("Источники SQL-консоли не настроены.")
            }
        } else {
            SqlConsoleSourceGroupTree(
                groups = displayGroups,
                selectedSourceNames = selectedSourceNames,
                onToggleSourceGroup = onToggleSourceGroup,
            ) { sourceName ->
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
}

@Composable
internal fun SqlConsoleSourceGroupTree(
    groups: List<SqlConsoleSourceGroup>,
    selectedSourceNames: List<String>,
    onToggleSourceGroup: (SqlConsoleSourceGroup, Boolean) -> Unit,
    sourceContent: @Composable (String) -> Unit,
) {
    groups.forEach { group ->
        val selectionState = sourceGroupSelectionState(group, selectedSourceNames)
        SqlConsoleSourceGroupCard(
            group = group,
            selectionState = selectionState,
            onToggle = {
                onToggleSourceGroup(group, selectionState != SqlConsoleSourceGroupSelectionState.ALL)
            },
            sourceContent = sourceContent,
        )
    }
}

@Composable
internal fun SqlConsoleSourceGroupCard(
    group: SqlConsoleSourceGroup,
    selectionState: SqlConsoleSourceGroupSelectionState,
    onToggle: () -> Unit,
    sourceContent: @Composable (String) -> Unit,
) {
    val selected = selectionState == SqlConsoleSourceGroupSelectionState.ALL
    var expanded by remember(group.name, group.synthetic) { mutableStateOf(true) }
    Div(attrs = {
        classes("sql-source-group-checkbox")
        if (selected) {
            classes("sql-source-group-checkbox-selected")
        }
        if (selectionState == SqlConsoleSourceGroupSelectionState.PARTIAL) {
            classes("sql-source-group-checkbox-partial")
        }
        if (group.synthetic) {
            classes("sql-source-group-checkbox-synthetic")
        }
    }) {
        Div({ classes("sql-source-group-checkbox-header") }) {
            Label(attrs = { classes("sql-source-group-checkbox-main") }) {
                Input(type = InputType.Checkbox, attrs = {
                    if (selected) {
                        attr("checked", "checked")
                    }
                    attr("aria-label", "Выбрать группу ${group.name}")
                    onClick { onToggle() }
                })
                Div({ classes("sql-source-group-checkbox-body") }) {
                    Div({ classes("sql-source-group-checkbox-head") }) {
                        Div({ classes("sql-source-group-checkbox-title") }) {
                            Span({ classes("sql-source-group-checkbox-name") }) {
                                Text(group.name)
                            }
                            Span({ classes("sql-source-group-checkbox-count") }) {
                                Text("${group.sources.size}")
                            }
                        }
                        Span({ classes("sql-source-group-checkbox-status") }) {
                            Text(translateSourceGroupSelectionState(selectionState))
                        }
                    }
                    buildSourceGroupMessage(group)?.let { message ->
                        Div({ classes("sql-source-group-checkbox-message") }) {
                            Text(message)
                        }
                    }
                }
            }
            Button(attrs = {
                classes("sql-source-group-toggle")
                if (expanded) {
                    classes("sql-source-group-toggle-expanded")
                }
                attr("type", "button")
                attr("title", if (expanded) "Свернуть группу" else "Раскрыть группу")
                attr("aria-label", if (expanded) "Свернуть группу" else "Раскрыть группу")
                if (group.sources.isEmpty()) {
                    disabled()
                }
                onClick { expanded = !expanded }
            }) { }
        }
        if (expanded && group.sources.isNotEmpty()) {
            Div({ classes("sql-source-group-sources") }) {
                group.sources.forEach { sourceName ->
                    sourceContent(sourceName)
                }
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
    val sourceDetail = sourceStatusDetail(sourceStatus)
    Label(attrs = {
        classes("sql-source-checkbox", "sql-source-checkbox-${sourceStatusTone(sourceStatus)}")
        if (selected) {
            classes("sql-source-checkbox-selected")
        }
        attr("title", sourceDetail ?: sourceName)
    }) {
        Input(type = InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            attr("aria-label", "Выбрать source $sourceName")
            onClick { onToggle() }
        })
        Div({ classes("sql-source-checkbox-body") }) {
            Div({ classes("sql-source-checkbox-head") }) {
                Div({ classes("sql-source-checkbox-title") }) {
                    Span({ classes("sql-source-checkbox-name") }) {
                        Text(sourceName)
                    }
                    Span({ classes("sql-source-checkbox-status-dot") }) { }
                }
                sourceStatus?.let { status ->
                    Span({ classes("sql-source-checkbox-status") }) {
                        Text(translateConnectionStatus(status.status))
                    }
                }
            }
            sourceDetail?.let { detail ->
                Div({ classes("sql-source-checkbox-message") }) {
                    Text(detail)
                }
            }
        }
    }
}

private fun translateSourceGroupSelectionState(
    selectionState: SqlConsoleSourceGroupSelectionState,
): String =
    when (selectionState) {
        SqlConsoleSourceGroupSelectionState.ALL -> "Все"
        SqlConsoleSourceGroupSelectionState.PARTIAL -> "Часть"
        SqlConsoleSourceGroupSelectionState.NONE -> "Нет"
    }

private fun buildSourceGroupMessage(group: SqlConsoleSourceGroup): String? =
    when {
        group.synthetic -> "Без явной группы"
        else -> null
    }

private fun sourceStatusDetail(sourceStatus: SqlConsoleSourceConnectionStatus?): String? =
    sourceStatus?.errorMessage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: sourceStatus?.message
            ?.trim()
            ?.takeIf {
                it.isNotEmpty() &&
                    it != "Подключение установлено." &&
                    it != "Статус появится после проверки подключения или выполнения SQL по этому source."
            }

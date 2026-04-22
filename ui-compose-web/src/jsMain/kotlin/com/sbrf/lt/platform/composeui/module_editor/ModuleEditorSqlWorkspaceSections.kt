package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlPreview(
    state: ModuleEditorPageState,
    selectedSqlPath: String?,
    onSelectSql: (String) -> Unit,
    onSqlChange: (String, String) -> Unit,
    onCreateSql: () -> Unit,
    onRenameSql: () -> Unit,
    onDeleteSql: () -> Unit,
) {
    val sqlFiles = buildDraftSqlFiles(state)
    val selectedSql = sqlFiles.firstOrNull { it.path == selectedSqlPath } ?: sqlFiles.firstOrNull()
    val selectedSqlContent = selectedSql?.let { sqlFile ->
        state.sqlContentsDraft[sqlFile.path] ?: sqlFile.content
    } ?: "-- SQL-ресурс не выбран"
    Div({ classes("panel") }) {
        Div({ classes("sql-catalog-layout") }) {
            Div({ classes("sql-catalog-sidebar") }) {
                Div({ classes("sql-catalog-toolbar") }) {
                    Div {
                        Div({ classes("sql-catalog-title") }) { Text("Каталог SQL-ресурсов") }
                        Div({ classes("sql-catalog-note") }) {
                            Text("Создание, переименование и удаление SQL выполняется здесь. Изменения сохраняются основным действием редактора.")
                        }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        WorkspaceActionButton("Создать SQL", "btn-outline-primary", small = true) { onCreateSql() }
                        WorkspaceActionButton("Переименовать", "btn-outline-secondary", disabled = selectedSql == null, small = true) { onRenameSql() }
                        WorkspaceActionButton("Удалить", "btn-outline-danger", disabled = selectedSql == null, small = true) { onDeleteSql() }
                    }
                }
                Div({ classes("sql-catalog-list") }) {
                    if (sqlFiles.isEmpty()) {
                        Div({ classes("text-secondary", "small") }) {
                            Text("SQL-ресурсы пока не созданы.")
                        }
                    }
                    sqlFiles.forEach { sqlFile ->
                        Button(attrs = {
                            classes("sql-catalog-item")
                            if (selectedSql?.path == sqlFile.path) {
                                classes("active")
                            }
                            attr("type", "button")
                            onClick { onSelectSql(sqlFile.path) }
                        }) {
                            Div({ classes("sql-catalog-item-title") }) { Text(sqlFile.label) }
                            Div({ classes("sql-catalog-item-meta") }) { Text(sqlFile.path) }
                        }
                    }
                }
            }

            Div({ classes("sql-catalog-workspace") }) {
                Div({ classes("sql-catalog-workspace-header") }) {
                    Div({ classes("sql-catalog-resource-title") }) {
                        Text(selectedSql?.label ?: "SQL-ресурс не выбран")
                    }
                    Div({ classes("sql-catalog-resource-meta", "text-secondary", "small") }) {
                        Text(selectedSql?.path ?: "-")
                    }
                    Div({ classes("sql-catalog-resource-usage") }) {
                        buildSqlUsageBadges(state, selectedSql?.path).forEach { badge ->
                            Span({
                                classes("sql-resource-usage-badge")
                                if (badge.muted) {
                                    classes("sql-resource-usage-badge-muted")
                                }
                            }) {
                                Text(badge.label)
                            }
                        }
                    }
                }
                MonacoEditorPane(
                    instanceKey = "module-editor-sql-${selectedSql?.path ?: "empty"}",
                    language = "sql",
                    value = selectedSqlContent,
                    readOnly = selectedSql == null,
                    onValueChange = { nextValue ->
                        val path = selectedSql?.path ?: return@MonacoEditorPane
                        onSqlChange(path, nextValue)
                    },
                )
            }
        }
    }
}

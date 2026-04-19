package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun CreateModulePanel(
    state: ModuleEditorPageState,
    onModuleCodeChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
    onConfigTextChange: (String) -> Unit,
    onRestoreTemplate: () -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    val draft = state.createModuleDraft
    val actionBusy = state.actionInProgress != null
    Div({ classes("panel", "mb-4") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "justify-content-between", "gap-3", "mb-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("Новый модуль") }
                Div({ classes("text-secondary", "small") }) {
                    Text("Создай DB-модуль и сразу открой его в Compose editor.")
                }
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onRestoreTemplate() }
                }) { Text("Восстановить шаблон") }
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onCancel() }
                }) { Text("Отмена") }
                Button(attrs = {
                    classes("btn", "btn-primary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onCreate() }
                }) { Text("Создать модуль") }
            }
        }

        Div({ classes("row", "g-4") }) {
            Div({ classes("col-12", "col-xl-4") }) {
                Div({ classes("module-metadata-card") }) {
                    Div({ classes("module-metadata-form-title") }) { Text("Параметры модуля") }
                    Div({ classes("module-metadata-form") }) {
                        MetadataTextField(
                            label = "Код модуля",
                            value = draft.moduleCode,
                            helpText = "Уникальный идентификатор модуля. Используется в URL и в каталоге.",
                            onCommit = onModuleCodeChange,
                        )
                        MetadataTextField(
                            label = "Название",
                            value = draft.title,
                            helpText = "Отображаемое имя модуля.",
                            onCommit = onTitleChange,
                        )
                        MetadataTextareaField(
                            label = "Описание",
                            value = draft.description,
                            rowsCount = 3,
                            helpText = "Кратко опиши назначение модуля.",
                            onCommit = onDescriptionChange,
                        )
                        MetadataTextField(
                            label = "Теги",
                            value = draft.tagsText,
                            helpText = "Через запятую. Пустые значения будут отброшены.",
                            onCommit = onTagsChange,
                        )
                        MetadataCheckboxField(
                            label = "Скрыть модуль в обычном каталоге UI",
                            checked = draft.hiddenFromUi,
                            helpText = "Если модуль скрыт, после создания каталог останется в режиме includeHidden.",
                            onCommit = onHiddenFromUiChange,
                        )
                    }
                }
            }
            Div({ classes("col-12", "col-xl-8") }) {
                Div({ classes("panel") }) {
                    Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-3") }) {
                        Div {
                            Div({ classes("panel-title", "mb-1") }) { Text("Стартовый application.yml") }
                            Div({ classes("text-secondary", "small") }) {
                                Text("Базовый шаблон уже валиден по структуре и подходит для дальнейшего редактирования.")
                            }
                        }
                    }
                    com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
                        instanceKey = "module-create-config",
                        language = "yaml",
                        value = draft.configText,
                        onValueChange = onConfigTextChange,
                    )
                }
            }
        }
    }
}

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
                        Div({ classes("sql-catalog-note") }) { Text("Создание, переименование и удаление SQL выполняется здесь. Изменения сохраняются основным действием редактора.") }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        Button(attrs = {
                            classes("btn", "btn-outline-primary", "btn-sm")
                            attr("type", "button")
                            onClick { onCreateSql() }
                        }) { Text("Создать SQL") }
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary", "btn-sm")
                            attr("type", "button")
                            if (selectedSql == null) {
                                disabled()
                            }
                            onClick { onRenameSql() }
                        }) { Text("Переименовать") }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            if (selectedSql == null) {
                                disabled()
                            }
                            onClick { onDeleteSql() }
                        }) { Text("Удалить") }
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
                com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
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

@Composable
internal fun ConfigPreview(
    configText: String,
    onConfigChange: (String) -> Unit,
) {
    com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
        instanceKey = "module-editor-config",
        language = "yaml",
        value = configText,
        onValueChange = onConfigChange,
    )
}

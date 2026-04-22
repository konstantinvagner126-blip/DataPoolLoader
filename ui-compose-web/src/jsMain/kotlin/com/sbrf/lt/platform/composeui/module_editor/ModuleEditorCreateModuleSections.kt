package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
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
                WorkspaceActionButton("Восстановить шаблон", "btn-outline-secondary", disabled = actionBusy) { onRestoreTemplate() }
                WorkspaceActionButton("Отмена", "btn-outline-secondary", disabled = actionBusy) { onCancel() }
                WorkspaceActionButton("Создать модуль", "btn-primary", disabled = actionBusy) { onCreate() }
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
                    MonacoEditorPane(
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

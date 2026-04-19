package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
internal fun MetadataForm(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    session: ModuleEditorSessionResponse,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
) {
    val module = session.module
    val metadataDraft = state.metadataDraft
    val metadataChanged = metadataDraft.title != module.title ||
        metadataDraft.description != (module.description ?: "") ||
        metadataDraft.tags != module.tags ||
        metadataDraft.hiddenFromUi != module.hiddenFromUi
    Div({ classes("panel", "module-metadata") }) {
        if (metadataChanged) {
            AlertBanner(
                "Метаданные изменены локально. Чтобы сохранить их, используй основное действие сохранения редактора.",
                "warning",
            )
        }
        Div({ classes("module-metadata-grid") }) {
            PreviewCard("Основное") {
                MetadataReadOnlyRow("Код модуля", module.id)
                MetadataTextField(
                    label = "Название",
                    value = metadataDraft.title,
                    helpText = "Отображаемое название модуля в каталоге.",
                    onCommit = onTitleChange,
                )
                MetadataTextareaField(
                    label = "Описание",
                    value = metadataDraft.description,
                    helpText = "Короткое описание, которое видно в каталоге модулей.",
                    rowsCount = 4,
                    onCommit = onDescriptionChange,
                )
                MetadataTextField(
                    label = "Теги",
                    value = metadataDraft.tags.joinToString(", "),
                    helpText = "Список тегов через запятую.",
                ) { rawValue ->
                    onTagsChange(
                        rawValue.split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    )
                }
                MetadataCheckboxField(
                    label = "Скрыть модуль из общего каталога UI",
                    checked = metadataDraft.hiddenFromUi,
                    helpText = if (route.storage == "database") {
                        "Полезно для DB-модулей, которые должны быть доступны только по прямому сценарию."
                    } else {
                        "Скрытый файловый модуль не показывается в основном каталоге."
                    },
                    onCommit = onHiddenFromUiChange,
                )
            }
            PreviewCard("DB lifecycle") {
                MetadataReadOnlyRow("Источник", translateSourceKind(session.sourceKind))
                MetadataReadOnlyRow("Текущая ревизия", session.currentRevisionId ?: "-")
                MetadataReadOnlyRow("Личный черновик", session.workingCopyId ?: "-")
                MetadataReadOnlyRow("Статус черновика", session.workingCopyStatus ?: "-")
                MetadataReadOnlyRow("Базовая ревизия", session.baseRevisionId ?: "-")
            }
        }
    }
}

@Composable
internal fun PreviewCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Div({ classes("module-metadata-card") }) {
        Div({ classes("module-metadata-form-title") }) { Text(title) }
        Div({ classes("module-metadata-form") }) {
            content()
        }
    }
}

@Composable
internal fun MetadataReadOnlyRow(
    label: String,
    value: String,
) {
    MetadataRowShell(label) {
        Div({ classes("module-metadata-value") }) { Text(value) }
    }
}

@Composable
internal fun MetadataTextField(
    label: String,
    value: String,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    MetadataEditableRow(label, helpText) {
            Input(type = org.jetbrains.compose.web.attributes.InputType.Text, attrs = {
                classes("form-control")
                value(draft)
                onInput { draft = it.value }
                onChange { if (draft != value) onCommit(draft) }
            })
    }
}

@Composable
internal fun MetadataTextareaField(
    label: String,
    value: String,
    rowsCount: Int,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    MetadataEditableRow(label, helpText) {
            TextArea(value = draft, attrs = {
                classes("form-control")
                rows(rowsCount)
                onInput { draft = it.value }
                onChange { if (draft != value) onCommit(draft) }
            })
    }
}

@Composable
internal fun MetadataCheckboxField(
    label: String,
    checked: Boolean,
    helpText: String = "",
    onCommit: (Boolean) -> Unit,
) {
    MetadataRowShell("Видимость") {
        MetadataValueBlock(helpText, helpClassName = "config-form-help mt-1") {
            Label(attrs = { classes("config-form-check") }) {
                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (checked) {
                        attr("checked", "checked")
                    }
                    onClick { onCommit(!checked) }
                })
                Span({ classes("form-check-label") }) { Text(label) }
            }
        }
    }
}

@Composable
private fun MetadataEditableRow(
    label: String,
    helpText: String,
    content: @Composable () -> Unit,
) {
    MetadataRowShell(label, useLabelTag = true) {
        MetadataValueBlock(helpText) {
            content()
        }
    }
}

@Composable
private fun MetadataRowShell(
    label: String,
    useLabelTag: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (useLabelTag) {
        Label(attrs = { classes("module-metadata-row") }) {
            Div({ classes("module-metadata-label") }) { Text(label) }
            content()
        }
        return
    }
    Div({ classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        content()
    }
}

@Composable
private fun MetadataValueBlock(
    helpText: String = "",
    helpClassName: String = "config-form-help mb-1",
    content: @Composable () -> Unit,
) {
    Div({ classes("module-metadata-value") }) {
        if (helpText.isNotBlank()) {
            Div({ classesFromString(helpClassName) }) { Text(helpText) }
        }
        content()
    }
}

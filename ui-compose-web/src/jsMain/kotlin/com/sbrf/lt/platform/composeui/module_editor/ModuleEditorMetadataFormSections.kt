package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div

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
            ModuleEditorPrimaryMetadataCard(
                route = route,
                metadataDraft = metadataDraft,
                moduleId = module.id,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
                onTagsChange = onTagsChange,
                onHiddenFromUiChange = onHiddenFromUiChange,
            )
            ModuleEditorLifecycleMetadataCard(session)
        }
    }
}

@Composable
private fun ModuleEditorPrimaryMetadataCard(
    route: ModuleEditorRouteState,
    metadataDraft: ModuleMetadataDraft,
    moduleId: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
) {
    PreviewCard("Основное") {
        MetadataReadOnlyRow("Код модуля", moduleId)
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
}

@Composable
private fun ModuleEditorLifecycleMetadataCard(
    session: ModuleEditorSessionResponse,
) {
    PreviewCard("DB lifecycle") {
        MetadataReadOnlyRow("Источник", translateSourceKind(session.sourceKind))
        MetadataReadOnlyRow("Текущая ревизия", session.currentRevisionId ?: "-")
        MetadataReadOnlyRow("Личный черновик", session.workingCopyId ?: "-")
        MetadataReadOnlyRow("Статус черновика", session.workingCopyStatus ?: "-")
        MetadataReadOnlyRow("Базовая ревизия", session.baseRevisionId ?: "-")
    }
}

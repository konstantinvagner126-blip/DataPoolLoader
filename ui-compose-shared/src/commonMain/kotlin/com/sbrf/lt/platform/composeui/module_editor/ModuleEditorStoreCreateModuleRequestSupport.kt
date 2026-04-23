package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreCreateModuleRequestSupport {
    fun validateDraft(draft: CreateModuleDraft): String? =
        when {
            draft.moduleCode.isBlank() -> "Укажи код модуля."
            draft.title.isBlank() -> "Укажи название модуля."
            draft.configText.isBlank() -> "Стартовый application.yml не должен быть пустым."
            else -> null
        }

    fun buildRequest(draft: CreateModuleDraft): CreateDbModuleRequestDto =
        CreateDbModuleRequestDto(
            moduleCode = draft.moduleCode.trim(),
            title = draft.title.trim(),
            description = draft.description.trim().ifBlank { null },
            tags = parseTags(draft.tagsText),
            configText = draft.configText,
            hiddenFromUi = draft.hiddenFromUi,
        )

    fun parseTags(rawValue: String): List<String> =
        rawValue.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

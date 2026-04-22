package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDatabaseLifecycleActionSupport(
    private val api: ModuleEditorApi,
    private val loadingSupport: ModuleEditorStoreLoadingSupport,
) {
    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val draft = current.createModuleDraft
        if (draft.moduleCode.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи код модуля.",
            )
        }
        if (draft.title.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи название модуля.",
            )
        }
        if (draft.configText.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Стартовый application.yml не должен быть пустым.",
            )
        }
        return runCatching {
            val response = api.createDatabaseModule(
                CreateDbModuleRequestDto(
                    moduleCode = draft.moduleCode.trim(),
                    title = draft.title.trim(),
                    description = draft.description.trim().ifBlank { null },
                    tags = parseTags(draft.tagsText),
                    configText = draft.configText,
                    hiddenFromUi = draft.hiddenFromUi,
                ),
            )
            val nextRoute = route.copy(
                moduleId = response.moduleCode,
                includeHidden = route.includeHidden || draft.hiddenFromUi,
            )
            val loaded = loadingSupport.load(nextRoute)
            loaded.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = null,
                successMessage = response.message,
                activeTab = ModuleEditorTab.SETTINGS,
                createModuleDialogOpen = false,
                createModuleDraft = CreateModuleDraft(),
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось создать модуль.",
            )
        }
    }

    suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.deleteDatabaseModule(moduleId)
            val loaded = loadingSupport.load(route.copy(moduleId = null))
            loaded.copy(
                loading = false,
                errorMessage = null,
                successMessage = response.message,
                actionInProgress = null,
                createModuleDialogOpen = false,
                createModuleDraft = CreateModuleDraft(),
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось удалить модуль.",
            )
        }
    }

    private fun parseTags(rawValue: String): List<String> =
        rawValue.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

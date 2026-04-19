package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreActionSupport(
    private val api: ModuleEditorApi,
    private val loadingSupport: ModuleEditorStoreLoadingSupport,
) {
    suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.saveFilesModule(moduleId, current.toSaveRequest())
            loadingSupport.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось сохранить модуль.",
            )
        }
    }

    suspend fun saveDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.saveDatabaseWorkingCopy(moduleId, current.toSaveRequest())
            loadingSupport.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось сохранить черновик.",
            )
        }
    }

    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.discardDatabaseWorkingCopy(moduleId)
            loadingSupport.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось сбросить черновик.",
            )
        }
    }

    suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.publishDatabaseWorkingCopy(moduleId)
            loadingSupport.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось опубликовать черновик.",
            )
        }
    }

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startFilesRun(
                StartRunRequestDto(
                    moduleId = moduleId,
                    configText = current.configTextDraft,
                    sqlFiles = current.sqlContentsDraft,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить модуль.",
            )
        }
    }

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startDatabaseRun(moduleId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить модуль из базы данных.",
            )
        }
    }

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

    private fun ModuleEditorPageState.toSaveRequest(): SaveModuleRequestDto =
        SaveModuleRequestDto(
            configText = configTextDraft,
            sqlFiles = sqlContentsDraft,
            title = metadataDraft.title,
            description = metadataDraft.description.ifBlank { null },
            tags = metadataDraft.tags,
            hiddenFromUi = metadataDraft.hiddenFromUi,
        )

    private fun parseTags(rawValue: String): List<String> =
        rawValue.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

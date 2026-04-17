package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.browser.window

class ModuleEditorStore(
    private val api: ModuleEditorApi,
) {
    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
        return runCatching {
            if (route.storage == "database") {
                val catalog = api.loadDatabaseCatalog(route.includeHidden)
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadDatabaseSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncBrowserUrl(route.storage, selectedModuleId, route.includeHidden)
                ModuleEditorPageState(
                    loading = false,
                    errorMessage = null,
                    successMessage = null,
                    actionInProgress = null,
                    databaseCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                    session = session,
                    selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
                    configTextDraft = session?.module?.configText.orEmpty(),
                    sqlContentsDraft = session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
                    configFormState = configForm?.state,
                    configFormError = configForm?.errorMessage,
                    configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
                )
            } else {
                val catalog = api.loadFilesCatalog()
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadFilesSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncBrowserUrl(route.storage, selectedModuleId, route.includeHidden)
                ModuleEditorPageState(
                    loading = false,
                    errorMessage = null,
                    successMessage = null,
                    actionInProgress = null,
                    filesCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                    session = session,
                    selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
                    configTextDraft = session?.module?.configText.orEmpty(),
                    sqlContentsDraft = session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
                    configFormState = configForm?.state,
                    configFormError = configForm?.errorMessage,
                    configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
                )
            }
        }.getOrElse { error ->
            ModuleEditorPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить редактор модуля.",
            )
        }
    }

    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState {
        return runCatching {
            val session = if (route.storage == "database") {
                api.loadDatabaseSession(moduleId)
            } else {
                api.loadFilesSession(moduleId)
            }
            val configForm = loadConfigFormSnapshot(session.module.configText)
            syncBrowserUrl(route.storage, moduleId, route.includeHidden)
            current.copy(
                loading = false,
                errorMessage = null,
                successMessage = null,
                actionInProgress = null,
                selectedModuleId = moduleId,
                session = session,
                selectedSqlPath = session.module.sqlFiles.firstOrNull()?.path,
                configTextDraft = session.module.configText,
                sqlContentsDraft = session.module.sqlFiles.associate { it.path to it.content },
                configFormState = configForm.state,
                configFormError = configForm.errorMessage,
                configFormSourceText = if (configForm.state != null) session.module.configText else "",
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить выбранный модуль.",
            )
        }
    }

    fun selectTab(
        current: ModuleEditorPageState,
        tab: ModuleEditorTab,
    ): ModuleEditorPageState =
        current.copy(activeTab = tab)

    fun selectSqlResource(
        current: ModuleEditorPageState,
        path: String,
    ): ModuleEditorPageState =
        current.copy(selectedSqlPath = path)

    fun updateConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        if (current.configTextDraft == value) {
            current
        } else {
            current.copy(
                configTextDraft = value,
            )
        }

    fun updateSqlText(
        current: ModuleEditorPageState,
        path: String,
        value: String,
    ): ModuleEditorPageState {
        if (current.sqlContentsDraft[path] == value) {
            return current
        }
        return current.copy(
            sqlContentsDraft = current.sqlContentsDraft + (path to value),
        )
    }

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

    fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        current.copy(
            configFormState = formState,
            configFormError = null,
        )

    suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState {
        if (current.configTextDraft.isBlank()) {
            return current.copy(
                configFormState = null,
                configFormError = "application.yml пустой. Восстанови конфиг или открой YAML-вкладку.",
                configFormLoading = false,
                configFormSourceText = "",
            )
        }
        return runCatching {
            val formState = api.parseConfigForm(current.configTextDraft)
            current.copy(
                configFormState = formState,
                configFormError = null,
                configFormLoading = false,
                configFormSourceText = current.configTextDraft,
            )
        }.getOrElse { error ->
            current.copy(
                configFormLoading = false,
                configFormError = error.message ?: "Не удалось разобрать application.yml для визуальной формы.",
            )
        }
    }

    suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        runCatching {
            val response = api.applyConfigForm(current.configTextDraft, formState)
            current.copy(
                configTextDraft = response.configText,
                configFormState = response.formState,
                configFormError = null,
                configFormLoading = false,
                configFormSourceText = response.configText,
            )
        }.getOrElse { error ->
            current.copy(
                configFormError = error.message ?: "Не удалось применить изменения формы к application.yml.",
                configFormLoading = false,
            )
        }

    fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(configFormLoading = true, configFormError = null)

    suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        val session = current.session ?: return current
        return runCatching {
            val response = api.saveFilesModule(moduleId, current.toSaveRequest(session))
            refreshSelectedModule(current, route, response.message)
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
        val session = current.session ?: return current
        return runCatching {
            val response = api.saveDatabaseWorkingCopy(moduleId, current.toSaveRequest(session))
            refreshSelectedModule(current, route, response.message)
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
            refreshSelectedModule(current, route, response.message)
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
            refreshSelectedModule(current, route, response.message)
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
                successMessage = "Запуск файлового модуля '$moduleId' начат.",
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
            val response = api.startDatabaseRun(moduleId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = response.message,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить модуль из базы данных.",
            )
        }
    }

    private fun resolveSelectedModuleId(
        preferredId: String?,
        moduleIds: List<String>,
    ): String? =
        when {
            preferredId != null && moduleIds.contains(preferredId) -> preferredId
            else -> moduleIds.firstOrNull()
        }

    private fun syncBrowserUrl(
        storage: String,
        moduleId: String?,
        includeHidden: Boolean,
    ) {
        val query = buildString {
            append("?storage=")
            append(storage)
            if (!moduleId.isNullOrBlank()) {
                append("&module=")
                append(moduleId)
            }
            if (includeHidden) {
                append("&includeHidden=true")
            }
        }
        window.history.replaceState(null, "", "/compose-editor$query")
    }

    private suspend fun refreshSelectedModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        successMessage: String,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        val refreshed = selectModule(
            current.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = null,
                successMessage = successMessage,
            ),
            route,
            moduleId,
        )
        return refreshed.copy(
            activeTab = current.activeTab,
            successMessage = successMessage,
        )
    }

    private fun ModuleEditorPageState.toSaveRequest(session: ModuleEditorSessionResponse): SaveModuleRequestDto =
        SaveModuleRequestDto(
            configText = configTextDraft,
            sqlFiles = sqlContentsDraft,
            title = session.module.title,
            description = session.module.description,
            tags = session.module.tags,
            hiddenFromUi = session.module.hiddenFromUi,
        )

    private suspend fun loadConfigFormSnapshot(configText: String): ConfigFormSnapshot =
        runCatching {
            ConfigFormSnapshot(
                state = api.parseConfigForm(configText),
                errorMessage = null,
            )
        }.getOrElse { error ->
            ConfigFormSnapshot(
                state = null,
                errorMessage = error.message ?: "Не удалось собрать визуальную форму.",
            )
        }

    private data class ConfigFormSnapshot(
        val state: ConfigFormStateDto?,
        val errorMessage: String?,
    )
}

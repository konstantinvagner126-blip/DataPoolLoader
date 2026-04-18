package com.sbrf.lt.platform.composeui.module_editor

class ModuleEditorStore(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit = { _, _, _ -> },
) {
    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
        return runCatching {
            if (route.storage == "database") {
                val catalog = api.loadDatabaseCatalog(route.includeHidden)
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadDatabaseSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncRoute(route.storage, selectedModuleId, route.includeHidden)
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
                    metadataDraft = session?.module?.let(::toMetadataDraft) ?: ModuleMetadataDraft(),
                    configFormState = configForm?.state,
                    configFormError = configForm?.errorMessage,
                    configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
                )
            } else {
                val catalog = api.loadFilesCatalog()
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadFilesSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncRoute(route.storage, selectedModuleId, route.includeHidden)
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
                    metadataDraft = session?.module?.let(::toMetadataDraft) ?: ModuleMetadataDraft(),
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
            syncRoute(route.storage, moduleId, route.includeHidden)
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
                metadataDraft = toMetadataDraft(session.module),
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
            current.copy(configTextDraft = value)
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

    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val nextPath = normalizeSqlResourceKey(rawName)
            ?: return current.copy(errorMessage = null, successMessage = null)
        if (current.sqlContentsDraft.containsKey(nextPath)) {
            return current.copy(
                errorMessage = "SQL-ресурс '$nextPath' уже существует.",
                successMessage = null,
            )
        }
        return current.copy(
            errorMessage = null,
            successMessage = "Создан SQL-ресурс '$nextPath'. Сохрани модуль, чтобы зафиксировать изменения.",
            selectedSqlPath = nextPath,
            sqlContentsDraft = sortSqlContents(current.sqlContentsDraft + (nextPath to "")),
        )
    }

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        val currentPath = current.selectedSqlPath
            ?: return current.copy(errorMessage = "Сначала выбери SQL-ресурс.", successMessage = null)
        val nextPath = normalizeSqlResourceKey(rawName, currentPath)
            ?: return current.copy(errorMessage = null, successMessage = null)
        if (nextPath == currentPath) {
            return current.copy(errorMessage = null, successMessage = null)
        }
        if (current.sqlContentsDraft.containsKey(nextPath)) {
            return current.copy(
                errorMessage = "SQL-ресурс '$nextPath' уже существует.",
                successMessage = null,
            )
        }

        val renamedSqlContents = current.sqlContentsDraft.toMutableMap().also { contents ->
            val currentValue = contents.remove(currentPath).orEmpty()
            contents[nextPath] = currentValue
        }.let(::sortSqlContents)

        var nextState = current.copy(
            errorMessage = null,
            successMessage = null,
            selectedSqlPath = nextPath,
            sqlContentsDraft = renamedSqlContents,
        )

        val formState = current.configFormState
        if (formState != null && sqlResourceUsedByForm(formState, currentPath)) {
            val updatedFormState = renameSqlResourceInFormState(formState, currentPath, nextPath)
            nextState = applyConfigForm(
                nextState.copy(
                    configFormState = updatedFormState,
                    configFormLoading = false,
                    configFormError = null,
                ),
                updatedFormState,
            )
        }

        return nextState.copy(
            errorMessage = null,
            successMessage = "SQL-ресурс переименован: '$currentPath' -> '$nextPath'.",
        )
    }

    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState {
        val currentPath = current.selectedSqlPath
            ?: return current.copy(errorMessage = "Сначала выбери SQL-ресурс.", successMessage = null)
        val usages = buildSqlResourceUsages(current.configFormState, currentPath)
        if (usages.isNotEmpty()) {
            return current.copy(
                errorMessage = "Нельзя удалить SQL-ресурс, пока он используется: ${usages.joinToString(", ")}.",
                successMessage = null,
            )
        }

        val nextSqlContents = current.sqlContentsDraft
            .filterKeys { it != currentPath }
            .let(::sortSqlContents)
        return current.copy(
            errorMessage = null,
            successMessage = "SQL-ресурс '$currentPath' удален. Сохрани модуль, чтобы зафиксировать изменения.",
            selectedSqlPath = nextSqlContents.keys.firstOrNull(),
            sqlContentsDraft = nextSqlContents,
        )
    }

    fun updateMetadataTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(title = value))

    fun updateMetadataDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(description = value))

    fun updateMetadataTags(
        current: ModuleEditorPageState,
        value: List<String>,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(tags = value))

    fun updateMetadataHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(hiddenFromUi = value))

    fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDialogOpen = true,
            createModuleDraft = CreateModuleDraft(),
            errorMessage = null,
            successMessage = null,
        )

    fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDialogOpen = false,
            createModuleDraft = CreateModuleDraft(),
        )

    fun updateCreateModuleCode(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(moduleCode = value))

    fun updateCreateModuleTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(title = value))

    fun updateCreateModuleDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(description = value))

    fun updateCreateModuleTagsText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(tagsText = value))

    fun updateCreateModuleHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(hiddenFromUi = value))

    fun updateCreateModuleConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(configText = value))

    fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDraft = current.createModuleDraft.copy(configText = defaultCreateModuleConfigTemplate()),
        )

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

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
            val loaded = load(nextRoute)
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
            val catalog = api.loadDatabaseCatalog(route.includeHidden)
            val nextSelectedModuleId = catalog.modules.firstOrNull()?.id
            val session = nextSelectedModuleId?.let { api.loadDatabaseSession(it) }
            val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
            syncRoute(route.storage, nextSelectedModuleId, route.includeHidden)
            ModuleEditorPageState(
                loading = false,
                errorMessage = null,
                successMessage = response.message,
                actionInProgress = null,
                databaseCatalog = catalog,
                selectedModuleId = nextSelectedModuleId,
                session = session,
                selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
                configTextDraft = session?.module?.configText.orEmpty(),
                sqlContentsDraft = session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
                metadataDraft = session?.module?.let(::toMetadataDraft) ?: ModuleMetadataDraft(),
                createModuleDialogOpen = false,
                createModuleDraft = CreateModuleDraft(),
                configFormState = configForm?.state,
                configFormError = configForm?.errorMessage,
                configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось удалить модуль.",
            )
        }
    }

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

    private fun resolveSelectedModuleId(
        preferredId: String?,
        moduleIds: List<String>,
    ): String? =
        when {
            preferredId != null && moduleIds.contains(preferredId) -> preferredId
            else -> moduleIds.firstOrNull()
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
            title = metadataDraft.title,
            description = metadataDraft.description.ifBlank { null },
            tags = metadataDraft.tags,
            hiddenFromUi = metadataDraft.hiddenFromUi,
        )

    private fun toMetadataDraft(module: ModuleDetailsResponse): ModuleMetadataDraft =
        ModuleMetadataDraft(
            title = module.title,
            description = module.description ?: "",
            tags = module.tags,
            hiddenFromUi = module.hiddenFromUi,
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

    private fun parseTags(rawValue: String): List<String> =
        rawValue.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun sqlResourceUsedByForm(
        formState: ConfigFormStateDto,
        path: String,
    ): Boolean =
        formState.commonSqlFile == path || formState.sources.any { it.sqlFile == path }

    private fun renameSqlResourceInFormState(
        formState: ConfigFormStateDto,
        currentPath: String,
        nextPath: String,
    ): ConfigFormStateDto =
        formState.copy(
            commonSqlFile = formState.commonSqlFile?.takeUnless { it == currentPath } ?: nextPath.takeIf { formState.commonSqlFile == currentPath },
            sources = formState.sources.map { source ->
                if (source.sqlFile == currentPath) {
                    source.copy(sqlFile = nextPath)
                } else {
                    source
                }
            },
        )

    private fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String> {
        if (formState == null) {
            return emptyList()
        }
        return buildList {
            if (formState.commonSqlFile == path) {
                add("SQL по умолчанию")
            }
            formState.sources.forEach { source ->
                if (source.sqlFile == path) {
                    add("Источник: ${source.name}")
                }
            }
        }
    }

    private fun normalizeSqlResourceKey(
        rawName: String,
        fallbackValue: String = "",
    ): String? {
        val value = rawName.trim()
        if (value.isBlank()) {
            return null
        }
        if (value.startsWith("classpath:")) {
            return value
        }
        if (value.endsWith(".sql", ignoreCase = true)) {
            val normalized = value.removePrefix("/")
            return if (normalized.startsWith("sql/")) {
                "classpath:$normalized"
            } else {
                "classpath:sql/$normalized"
            }
        }

        val normalized = value
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s_-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("_+"), "-")
        val fallback = fallbackValue
            .removePrefix("classpath:sql/")
            .removeSuffix(".sql")
            .trim()
        val baseName = normalized.ifBlank { fallback }
        return baseName.takeIf { it.isNotBlank() }?.let { "classpath:sql/$it.sql" }
    }

    private fun sortSqlContents(sqlContents: Map<String, String>): Map<String, String> =
        sqlContents.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.toPair() }

    private data class ConfigFormSnapshot(
        val state: ConfigFormStateDto?,
        val errorMessage: String?,
    )
}

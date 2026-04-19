package com.sbrf.lt.platform.composeui.module_editor


class ModuleEditorStore(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit = { _, _, _ -> },
) {
    private val loadingSupport = ModuleEditorStoreLoadingSupport(api, syncRoute)
    private val actionSupport = ModuleEditorStoreActionSupport(api, loadingSupport)

    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(successMessage = null)

    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(errorMessage = null)

    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState =
        loadingSupport.load(route)

    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState =
        loadingSupport.selectModule(current, route, moduleId)

    suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        loadingSupport.refreshCatalog(current, route)

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
    ): ModuleEditorPageState =
        actionSupport.saveFilesModule(current, route)

    suspend fun saveDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.saveDatabaseWorkingCopy(current, route)

    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.discardDatabaseWorkingCopy(current, route)

    suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.publishDatabaseWorkingCopy(current, route)

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runFilesModule(current)

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runDatabaseModule(current)

    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.createDatabaseModule(current, route)

    suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.deleteDatabaseModule(current, route)

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
}

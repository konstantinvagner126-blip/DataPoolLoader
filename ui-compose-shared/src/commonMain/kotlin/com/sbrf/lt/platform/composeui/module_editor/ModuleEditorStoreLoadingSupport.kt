package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogDiagnostics
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleEditorStoreLoadingSupport(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
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
                    metadataDraft = session?.module?.let(::toModuleMetadataDraft) ?: ModuleMetadataDraft(),
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
                    metadataDraft = session?.module?.let(::toModuleMetadataDraft) ?: ModuleMetadataDraft(),
                    configFormState = configForm?.state,
                    configFormError = configForm?.errorMessage,
                    configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
                )
            }
        }.recoverCatching { error ->
            loadDatabaseFallbackState()
                ?: throw error
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
                metadataDraft = toModuleMetadataDraft(session.module),
                configFormState = configForm.state,
                configFormError = configForm.errorMessage,
                configFormSourceText = if (configForm.state != null) session.module.configText else "",
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить выбранный модуль.",
            )
        }.let { nextState ->
            if (route.storage != "database" || nextState.session != null || nextState.errorMessage == null) {
                nextState
            } else {
                loadDatabaseFallbackState(nextState) ?: nextState
            }
        }
    }

    suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        runCatching {
            if (route.storage == "database") {
                val catalog = api.loadDatabaseCatalog(route.includeHidden)
                val selectedModuleId = current.selectedModuleId
                    ?.takeIf { moduleId -> catalog.modules.any { it.id == moduleId } }
                    ?: catalog.modules.firstOrNull()?.id
                current.copy(
                    loading = false,
                    databaseCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                )
            } else {
                val catalog = api.loadFilesCatalog()
                val selectedModuleId = current.selectedModuleId
                    ?.takeIf { moduleId -> catalog.modules.any { it.id == moduleId } }
                    ?: catalog.modules.firstOrNull()?.id
                current.copy(
                    loading = false,
                    filesCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                )
            }
        }.getOrElse {
            if (route.storage == "database") {
                loadDatabaseFallbackState(current) ?: current
            } else {
                current
            }
        }

    suspend fun refreshSelectedModule(
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

    suspend fun loadConfigFormSnapshot(configText: String): ConfigFormSnapshot =
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

    private suspend fun loadDatabaseFallbackState(current: ModuleEditorPageState? = null): ModuleEditorPageState? {
        val runtimeContext = runCatching { api.loadRuntimeContext() }.getOrNull() ?: return null
        if (runtimeContext.requestedMode != ModuleStoreMode.DATABASE || runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) {
            return null
        }
        return createDatabaseFallbackState(runtimeContext, current)
    }

    private fun createDatabaseFallbackState(
        runtimeContext: RuntimeContext,
        current: ModuleEditorPageState?,
    ): ModuleEditorPageState =
        (current ?: ModuleEditorPageState()).copy(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            databaseCatalog = DatabaseModulesCatalogResponse(
                runtimeContext = runtimeContext,
                diagnostics = current?.databaseCatalog?.diagnostics ?: ModuleCatalogDiagnostics(),
                modules = emptyList(),
            ),
            selectedModuleId = null,
            session = null,
            selectedSqlPath = null,
            configTextDraft = "",
            sqlContentsDraft = emptyMap(),
            metadataDraft = ModuleMetadataDraft(),
            configFormState = null,
            configFormLoading = false,
            configFormError = null,
            configFormSourceText = "",
            createModuleDialogOpen = false,
        )

    private fun resolveSelectedModuleId(
        preferredId: String?,
        moduleIds: List<String>,
    ): String? =
        when {
            preferredId != null && moduleIds.contains(preferredId) -> preferredId
            else -> moduleIds.firstOrNull()
        }

}

internal fun toModuleMetadataDraft(module: ModuleDetailsResponse): ModuleMetadataDraft =
    ModuleMetadataDraft(
        title = module.title,
        description = module.description ?: "",
        tags = module.tags,
        hiddenFromUi = module.hiddenFromUi,
    )

internal data class ConfigFormSnapshot(
    val state: ConfigFormStateDto?,
    val errorMessage: String?,
)

package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreCatalogLoadingSupport(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
    private val stateFactory: ModuleEditorStoreStateFactory,
    private val fallbackSupport: ModuleEditorStoreFallbackSupport,
) {
    suspend fun load(
        route: ModuleEditorRouteState,
        loadConfigFormSnapshot: suspend (String) -> ConfigFormSnapshot,
    ): ModuleEditorPageState {
        return runCatching {
            if (route.storage == "database") {
                val catalog = api.loadDatabaseCatalog(route.includeHidden)
                val selectedModuleId = stateFactory.resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadDatabaseSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncRoute(route.storage, selectedModuleId, route.includeHidden)
                stateFactory.createDatabaseLoadedState(catalog, selectedModuleId, session, configForm)
            } else {
                val catalog = api.loadFilesCatalog()
                val selectedModuleId = stateFactory.resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadFilesSession(moduleId) }
                val configForm = session?.let { loadConfigFormSnapshot(it.module.configText) }
                syncRoute(route.storage, selectedModuleId, route.includeHidden)
                stateFactory.createFilesLoadedState(catalog, selectedModuleId, session, configForm)
            }
        }.recoverCatching { error ->
            fallbackSupport.loadDatabaseFallbackState()
                ?: throw error
        }.getOrElse { error ->
            ModuleEditorPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить редактор модуля.",
            )
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
                fallbackSupport.loadDatabaseFallbackState(current) ?: current
            } else {
                current
            }
        }
}

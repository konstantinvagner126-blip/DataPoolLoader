package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreCatalogLoadingSupport(
    private val storageReadSupport: ModuleEditorStorageReadSupport,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
    private val stateFactory: ModuleEditorStoreStateFactory,
    private val fallbackSupport: ModuleEditorStoreFallbackSupport,
) {
    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
        return runCatching {
            val snapshot = storageReadSupport.loadCatalogSnapshot(route)
            syncRoute(route.storage, snapshot.selectedModuleId, route.includeHidden)
            stateFactory.createLoadedState(snapshot)
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
            val snapshot = storageReadSupport.refreshCatalogSnapshot(route, current.selectedModuleId)
            stateFactory.applyCatalogRefresh(current, snapshot)
        }.getOrElse {
            if (route.storage == "database") {
                fallbackSupport.loadDatabaseFallbackState(current) ?: current
            } else {
                current
            }
        }
}

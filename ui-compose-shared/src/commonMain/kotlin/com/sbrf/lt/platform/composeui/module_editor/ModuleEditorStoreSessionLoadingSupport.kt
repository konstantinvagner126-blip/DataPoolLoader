package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSessionLoadingSupport(
    private val storageReadSupport: ModuleEditorStorageReadSupport,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
    private val stateFactory: ModuleEditorStoreStateFactory,
    private val fallbackSupport: ModuleEditorStoreFallbackSupport,
) {
    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState {
        return runCatching {
            val snapshot = storageReadSupport.loadSessionSnapshot(route, moduleId)
            syncRoute(route.storage, moduleId, route.includeHidden)
            stateFactory.applySelectedSession(current, snapshot)
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить выбранный модуль.",
            )
        }.let { nextState ->
            if (route.storage != "database" || nextState.session != null || nextState.errorMessage == null) {
                nextState
            } else {
                fallbackSupport.loadDatabaseFallbackState(nextState) ?: nextState
            }
        }
    }

    suspend fun refreshSelectedModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        successMessage: String,
        selectModule: suspend (ModuleEditorPageState, ModuleEditorRouteState, String) -> ModuleEditorPageState,
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
}

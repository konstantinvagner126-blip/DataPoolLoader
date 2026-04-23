package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSessionLoadingSupport(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
    private val stateFactory: ModuleEditorStoreStateFactory,
    private val configFormSnapshotStore: ModuleEditorConfigFormSnapshotStore,
    private val fallbackSupport: ModuleEditorStoreFallbackSupport,
) {
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
            val configForm = configFormSnapshotStore.loadSnapshot(session.module.configText)
            syncRoute(route.storage, moduleId, route.includeHidden)
            stateFactory.applySelectedSession(current, moduleId, session, configForm)
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

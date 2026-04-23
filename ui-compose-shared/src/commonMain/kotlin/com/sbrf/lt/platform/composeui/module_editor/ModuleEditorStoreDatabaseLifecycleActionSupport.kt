package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDatabaseLifecycleActionSupport(
    private val lifecycleStore: ModuleEditorDatabaseLifecycleStore,
    private val loadingStore: ModuleEditorLoadingStore,
) {
    private val requestSupport = ModuleEditorStoreCreateModuleRequestSupport()
    private val stateSupport = ModuleEditorStoreDatabaseLifecycleStateSupport()

    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val draft = current.createModuleDraft
        val validationError = requestSupport.validateDraft(draft)
        if (validationError != null) {
            return current.copy(
                actionInProgress = null,
                errorMessage = validationError,
            )
        }
        return runCatching {
            val response = lifecycleStore.createModule(requestSupport.buildRequest(draft))
            val nextRoute = stateSupport.nextRouteAfterCreate(route, draft, response)
            val loaded = loadingStore.load(nextRoute)
            stateSupport.applyCreatedModuleLoadedState(loaded, response)
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
            val response = lifecycleStore.deleteModule(moduleId)
            val loaded = loadingStore.load(stateSupport.nextRouteAfterDelete(route))
            stateSupport.applyDeletedModuleLoadedState(loaded, response)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось удалить модуль.",
            )
        }
    }
}

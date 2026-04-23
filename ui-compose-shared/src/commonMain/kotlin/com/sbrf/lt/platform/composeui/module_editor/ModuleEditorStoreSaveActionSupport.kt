package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSaveActionSupport(
    private val api: ModuleEditorApi,
    private val refreshStore: ModuleEditorSelectedModuleRefreshStore,
) {
    private val requestSupport = ModuleEditorStoreSaveRequestSupport()

    suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = api.saveFilesModule(moduleId, requestSupport.buildSaveRequest(current))
            refreshStore.refreshSelectedModule(current, route, response.message)
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
            val response = api.saveDatabaseWorkingCopy(moduleId, requestSupport.buildSaveRequest(current))
            refreshStore.refreshSelectedModule(current, route, response.message)
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
            refreshStore.refreshSelectedModule(current, route, response.message)
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
            refreshStore.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось опубликовать черновик.",
            )
        }
    }
}

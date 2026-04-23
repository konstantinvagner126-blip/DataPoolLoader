package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSaveActionSupport(
    private val saveStore: ModuleEditorStorageSaveStore,
    private val refreshStore: ModuleEditorSelectedModuleRefreshStore,
) {
    private val requestSupport = ModuleEditorStoreSaveRequestSupport()

    suspend fun saveModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = saveStore.save(route, moduleId, requestSupport.buildSaveRequest(current))
            refreshStore.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = if (route.storage == "database") {
                    error.message ?: "Не удалось сохранить черновик."
                } else {
                    error.message ?: "Не удалось сохранить модуль."
                },
            )
        }
    }

    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            val response = saveStore.discardWorkingCopy(moduleId)
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
            val response = saveStore.publishWorkingCopy(moduleId)
            refreshStore.refreshSelectedModule(current, route, response.message)
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось опубликовать черновик.",
            )
        }
    }
}

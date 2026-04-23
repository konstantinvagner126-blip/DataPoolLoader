package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunActionSupport(
    private val runStore: ModuleEditorStorageRunStore,
) {
    private val stateSupport = ModuleEditorStoreRunStateSupport()

    suspend fun runModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            runStore.run(route.storage, moduleId, current)
            stateSupport.runStarted(current)
        }.getOrElse { error ->
            stateSupport.runFailed(
                current = current,
                error = error,
                fallbackMessage = if (route.storage == "database") {
                    "Не удалось запустить модуль из базы данных."
                } else {
                    "Не удалось запустить модуль."
                },
            )
        }
    }
}

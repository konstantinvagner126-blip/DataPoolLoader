package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunActionSupport(
    private val runStore: ModuleEditorStorageRunStore,
) {
    private val stateSupport = ModuleEditorStoreRunStateSupport()

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState {
        return runModule(current, "files", "Не удалось запустить модуль.")
    }

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState {
        return runModule(current, "database", "Не удалось запустить модуль из базы данных.")
    }

    private suspend fun runModule(
        current: ModuleEditorPageState,
        storage: String,
        fallbackMessage: String,
    ): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            runStore.run(storage, moduleId, current)
            stateSupport.runStarted(current)
        }.getOrElse { error ->
            stateSupport.runFailed(current, error, fallbackMessage)
        }
    }
}

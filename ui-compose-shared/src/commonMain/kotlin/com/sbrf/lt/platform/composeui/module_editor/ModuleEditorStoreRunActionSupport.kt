package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunActionSupport(
    private val api: ModuleEditorApi,
) {
    private val requestSupport = ModuleEditorStoreRunRequestSupport()
    private val stateSupport = ModuleEditorStoreRunStateSupport()

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startFilesRun(requestSupport.buildFilesRunRequest(moduleId, current))
            stateSupport.runStarted(current)
        }.getOrElse { error ->
            stateSupport.runFailed(current, error, "Не удалось запустить модуль.")
        }
    }

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startDatabaseRun(moduleId)
            stateSupport.runStarted(current)
        }.getOrElse { error ->
            stateSupport.runFailed(current, error, "Не удалось запустить модуль из базы данных.")
        }
    }
}

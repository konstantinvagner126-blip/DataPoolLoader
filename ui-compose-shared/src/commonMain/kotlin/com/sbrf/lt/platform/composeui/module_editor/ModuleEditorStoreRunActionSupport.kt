package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunActionSupport(
    private val api: ModuleEditorApi,
) {
    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startFilesRun(
                StartRunRequestDto(
                    moduleId = moduleId,
                    configText = current.configTextDraft,
                    sqlFiles = current.sqlContentsDraft,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить модуль.",
            )
        }
    }

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState {
        val moduleId = current.selectedModuleId ?: return current
        return runCatching {
            api.startDatabaseRun(moduleId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить модуль из базы данных.",
            )
        }
    }
}

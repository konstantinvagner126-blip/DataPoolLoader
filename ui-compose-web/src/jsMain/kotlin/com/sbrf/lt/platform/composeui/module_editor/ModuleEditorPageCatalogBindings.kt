package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import kotlinx.browser.window
import kotlinx.coroutines.launch

internal class ModuleEditorPageCatalogBindings(
    private val context: ModuleEditorPageBindingContext,
) {
    fun openCreateModule() {
        context.updateState { context.store.openCreateModuleDialog(it) }
    }

    fun deleteModule() {
        val moduleId = context.currentState().selectedModuleId
        if (!moduleId.isNullOrBlank() && window.confirm("Удалить модуль '$moduleId'? Это действие необратимо.")) {
            context.scope.launch {
                val deletingState = context.store.beginAction(context.currentState(), "delete")
                val nextState = context.store.deleteDatabaseModule(deletingState, context.currentRoute())
                context.setState(nextState)
                context.setCurrentRoute(
                    context.currentRoute().copy(
                        moduleId = nextState.selectedModuleId,
                        openCreateDialog = false,
                    ),
                )
            }
        }
    }

    fun toggleIncludeHidden() {
        val nextRoute = context.currentRoute().copy(
            includeHidden = !context.currentRoute().includeHidden,
            openCreateDialog = false,
        )
        context.setCurrentRoute(nextRoute)
        context.scope.launch {
            context.setState(context.store.startLoading(context.currentState()))
            context.setState(context.store.load(nextRoute))
        }
    }

    fun selectModule(moduleId: String) {
        context.scope.launch {
            context.setState(context.store.startLoading(context.currentState()))
            context.setCurrentRoute(
                context.currentRoute().copy(
                    moduleId = moduleId,
                    openCreateDialog = false,
                ),
            )
            context.setState(context.store.selectModule(context.currentState(), context.currentRoute(), moduleId))
        }
    }

    fun selectCredentialsFile(file: org.w3c.files.File?) {
        context.updateUiState {
            it.copy(
                selectedCredentialsFile = file,
                credentialsUploadMessage = null,
            )
        }
    }

    fun uploadCredentials() {
        val file = context.currentUiState().selectedCredentialsFile ?: return
        val moduleId = context.currentState().selectedModuleId ?: return
        context.scope.launch {
            context.updateUiState {
                it.copy(
                    credentialsUploadInProgress = true,
                    credentialsUploadMessage = null,
                )
            }
            try {
                uploadCredentialsFile(context.credentialsHttpClient, file)
                context.setState(context.store.startLoading(context.currentState()))
                val refreshed = context.store.selectModule(context.currentState(), context.currentRoute(), moduleId)
                context.setState(
                    refreshed.copy(
                        successMessage = "Файл credential.properties загружен: ${file.name}.",
                    ),
                )
                context.updateUiState {
                    it.copy(
                        selectedCredentialsFile = null,
                        credentialsUploadMessage = "Статус credentials обновлен.",
                        credentialsUploadMessageLevel = "success",
                        credentialsUploadInProgress = false,
                    )
                }
            } catch (error: Throwable) {
                context.updateUiState {
                    it.copy(
                        credentialsUploadMessage = error.message ?: "Не удалось загрузить credential.properties.",
                        credentialsUploadMessageLevel = "warning",
                        credentialsUploadInProgress = false,
                    )
                }
            }
        }
    }
}

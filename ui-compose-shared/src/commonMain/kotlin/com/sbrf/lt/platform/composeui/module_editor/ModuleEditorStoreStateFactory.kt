package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse

internal class ModuleEditorStoreStateFactory {
    fun createDatabaseLoadedState(
        catalog: DatabaseModulesCatalogResponse,
        selectedModuleId: String?,
        session: ModuleEditorSessionResponse?,
        configForm: ConfigFormSnapshot?,
    ): ModuleEditorPageState =
        ModuleEditorPageState(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            databaseCatalog = catalog,
            selectedModuleId = selectedModuleId,
            session = session,
            selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
            configTextDraft = session?.module?.configText.orEmpty(),
            sqlContentsDraft = session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
            metadataDraft = session?.module?.let(::toModuleMetadataDraft) ?: ModuleMetadataDraft(),
            configFormState = configForm?.state,
            configFormError = configForm?.errorMessage,
            configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
        )

    fun createFilesLoadedState(
        catalog: FilesModulesCatalogResponse,
        selectedModuleId: String?,
        session: ModuleEditorSessionResponse?,
        configForm: ConfigFormSnapshot?,
    ): ModuleEditorPageState =
        ModuleEditorPageState(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            filesCatalog = catalog,
            selectedModuleId = selectedModuleId,
            session = session,
            selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
            configTextDraft = session?.module?.configText.orEmpty(),
            sqlContentsDraft = session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
            metadataDraft = session?.module?.let(::toModuleMetadataDraft) ?: ModuleMetadataDraft(),
            configFormState = configForm?.state,
            configFormError = configForm?.errorMessage,
            configFormSourceText = if (configForm?.state != null) session?.module?.configText.orEmpty() else "",
        )

    fun applySelectedSession(
        current: ModuleEditorPageState,
        moduleId: String,
        session: ModuleEditorSessionResponse,
        configForm: ConfigFormSnapshot,
    ): ModuleEditorPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            selectedModuleId = moduleId,
            session = session,
            selectedSqlPath = session.module.sqlFiles.firstOrNull()?.path,
            configTextDraft = session.module.configText,
            sqlContentsDraft = session.module.sqlFiles.associate { it.path to it.content },
            metadataDraft = toModuleMetadataDraft(session.module),
            configFormState = configForm.state,
            configFormError = configForm.errorMessage,
            configFormSourceText = if (configForm.state != null) session.module.configText else "",
        )

}

internal fun toModuleMetadataDraft(module: ModuleDetailsResponse): ModuleMetadataDraft =
    ModuleMetadataDraft(
        title = module.title,
        description = module.description ?: "",
        tags = module.tags,
        hiddenFromUi = module.hiddenFromUi,
    )

internal data class ConfigFormSnapshot(
    val state: ConfigFormStateDto?,
    val errorMessage: String?,
)

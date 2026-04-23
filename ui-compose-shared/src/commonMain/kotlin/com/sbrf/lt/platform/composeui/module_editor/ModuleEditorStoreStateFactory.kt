package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreStateFactory {
    fun createLoadedState(
        snapshot: ModuleEditorCatalogSnapshot,
    ): ModuleEditorPageState =
        ModuleEditorPageState(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            databaseCatalog = snapshot.databaseCatalog,
            filesCatalog = snapshot.filesCatalog,
            selectedModuleId = snapshot.selectedModuleId,
            session = snapshot.session,
            selectedSqlPath = snapshot.session?.module?.sqlFiles?.firstOrNull()?.path,
            configTextDraft = snapshot.session?.module?.configText.orEmpty(),
            sqlContentsDraft = snapshot.session?.module?.sqlFiles?.associate { it.path to it.content }.orEmpty(),
            metadataDraft = snapshot.session?.module?.let(::toModuleMetadataDraft) ?: ModuleMetadataDraft(),
            configFormState = snapshot.configForm?.state,
            configFormError = snapshot.configForm?.errorMessage,
            configFormSourceText = if (snapshot.configForm?.state != null) snapshot.session?.module?.configText.orEmpty() else "",
        )

    fun applySelectedSession(
        current: ModuleEditorPageState,
        snapshot: ModuleEditorSessionSnapshot,
    ): ModuleEditorPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            selectedModuleId = snapshot.moduleId,
            session = snapshot.session,
            selectedSqlPath = snapshot.session.module.sqlFiles.firstOrNull()?.path,
            configTextDraft = snapshot.session.module.configText,
            sqlContentsDraft = snapshot.session.module.sqlFiles.associate { it.path to it.content },
            metadataDraft = toModuleMetadataDraft(snapshot.session.module),
            configFormState = snapshot.configForm.state,
            configFormError = snapshot.configForm.errorMessage,
            configFormSourceText = if (snapshot.configForm.state != null) snapshot.session.module.configText else "",
        )

    fun applyCatalogRefresh(
        current: ModuleEditorPageState,
        snapshot: ModuleEditorCatalogRefreshSnapshot,
    ): ModuleEditorPageState =
        current.copy(
            loading = false,
            filesCatalog = snapshot.filesCatalog ?: current.filesCatalog,
            databaseCatalog = snapshot.databaseCatalog ?: current.databaseCatalog,
            selectedModuleId = snapshot.selectedModuleId,
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

package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogDiagnostics
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleEditorStoreFallbackSupport(
    private val api: ModuleEditorApi,
) {
    suspend fun loadDatabaseFallbackState(current: ModuleEditorPageState? = null): ModuleEditorPageState? {
        val runtimeContext = runCatching { api.loadRuntimeContext() }.getOrNull() ?: return null
        if (runtimeContext.requestedMode != ModuleStoreMode.DATABASE || runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) {
            return null
        }
        return createDatabaseFallbackState(runtimeContext, current)
    }

    private fun createDatabaseFallbackState(
        runtimeContext: RuntimeContext,
        current: ModuleEditorPageState?,
    ): ModuleEditorPageState =
        (current ?: ModuleEditorPageState()).copy(
            loading = false,
            errorMessage = null,
            successMessage = null,
            actionInProgress = null,
            databaseCatalog = DatabaseModulesCatalogResponse(
                runtimeContext = runtimeContext,
                diagnostics = current?.databaseCatalog?.diagnostics ?: ModuleCatalogDiagnostics(),
                modules = emptyList(),
            ),
            selectedModuleId = null,
            session = null,
            selectedSqlPath = null,
            configTextDraft = "",
            sqlContentsDraft = emptyMap(),
            metadataDraft = ModuleMetadataDraft(),
            configFormState = null,
            configFormLoading = false,
            configFormError = null,
            configFormSourceText = "",
            createModuleDialogOpen = false,
        )
}

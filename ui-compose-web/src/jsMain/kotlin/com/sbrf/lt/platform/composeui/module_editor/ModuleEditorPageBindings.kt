package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import kotlinx.coroutines.CoroutineScope
import org.w3c.files.File

internal data class ModuleEditorPageUiState(
    val runPanelState: ModuleRunsPageState = ModuleRunsPageState(loading = false, historyLimit = 3),
    val runPanelRefreshInProgress: Boolean = false,
    val selectedCredentialsFile: File? = null,
    val credentialsUploadMessage: String? = null,
    val credentialsUploadMessageLevel: String = "success",
    val credentialsUploadInProgress: Boolean = false,
)

internal data class ModuleEditorPageCallbacks(
    val onOpenCreateModule: () -> Unit,
    val onDeleteModule: () -> Unit,
    val onToggleIncludeHidden: () -> Unit,
    val onSelectModule: (String) -> Unit,
    val onCredentialsFileSelected: (File?) -> Unit,
    val onUploadCredentials: () -> Unit,
    val onSelectTab: (ModuleEditorTab) -> Unit,
    val onRun: () -> Unit,
    val onSave: () -> Unit,
    val onDiscardWorkingCopy: () -> Unit,
    val onPublishWorkingCopy: () -> Unit,
    val onReload: () -> Unit,
    val onModuleCodeChange: (String) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onTagsChange: (String) -> Unit,
    val onHiddenFromUiChange: (Boolean) -> Unit,
    val onConfigTextChange: (String) -> Unit,
    val onRestoreTemplate: () -> Unit,
    val onCloseCreateModuleDialog: () -> Unit,
    val onCreateModule: () -> Unit,
    val onRefreshFromConfig: () -> Unit,
    val onApplyFormState: (ConfigFormStateDto) -> Unit,
    val onSelectSql: (String) -> Unit,
    val onSqlChange: (String, String) -> Unit,
    val onCreateSql: () -> Unit,
    val onRenameSql: () -> Unit,
    val onDeleteSql: () -> Unit,
    val onConfigDraftChange: (String) -> Unit,
    val onMetadataTitleChange: (String) -> Unit,
    val onMetadataDescriptionChange: (String) -> Unit,
    val onMetadataTagsChange: (List<String>) -> Unit,
    val onMetadataHiddenFromUiChange: (Boolean) -> Unit,
)

internal fun moduleEditorPageCallbacks(
    store: ModuleEditorStore,
    credentialsHttpClient: ComposeHttpClient,
    scope: CoroutineScope,
    currentRoute: () -> ModuleEditorRouteState,
    setCurrentRoute: (ModuleEditorRouteState) -> Unit,
    currentState: () -> ModuleEditorPageState,
    setState: (ModuleEditorPageState) -> Unit,
    currentUiState: () -> ModuleEditorPageUiState,
    setUiState: (ModuleEditorPageUiState) -> Unit,
    refreshModuleCatalog: suspend () -> Unit,
    refreshEditorRunPanel: suspend (String) -> Unit,
): ModuleEditorPageCallbacks {
    val context = ModuleEditorPageBindingContext(
        store = store,
        credentialsHttpClient = credentialsHttpClient,
        scope = scope,
        currentRouteProvider = currentRoute,
        setCurrentRouteProvider = setCurrentRoute,
        currentStateProvider = currentState,
        setStateProvider = setState,
        currentUiStateProvider = currentUiState,
        setUiStateProvider = setUiState,
        refreshModuleCatalog = refreshModuleCatalog,
        refreshEditorRunPanel = refreshEditorRunPanel,
    )
    val catalogBindings = ModuleEditorPageCatalogBindings(context)
    val executionBindings = ModuleEditorPageExecutionBindings(context)
    val draftBindings = ModuleEditorPageDraftBindings(context)

    return ModuleEditorPageCallbacks(
        onOpenCreateModule = catalogBindings::openCreateModule,
        onDeleteModule = catalogBindings::deleteModule,
        onToggleIncludeHidden = catalogBindings::toggleIncludeHidden,
        onSelectModule = catalogBindings::selectModule,
        onCredentialsFileSelected = catalogBindings::selectCredentialsFile,
        onUploadCredentials = catalogBindings::uploadCredentials,
        onSelectTab = draftBindings::selectTab,
        onRun = executionBindings::runModule,
        onSave = executionBindings::saveModule,
        onDiscardWorkingCopy = executionBindings::discardWorkingCopy,
        onPublishWorkingCopy = executionBindings::publishWorkingCopy,
        onReload = executionBindings::reloadModule,
        onModuleCodeChange = draftBindings::updateCreateModuleCode,
        onTitleChange = draftBindings::updateCreateModuleTitle,
        onDescriptionChange = draftBindings::updateCreateModuleDescription,
        onTagsChange = draftBindings::updateCreateModuleTags,
        onHiddenFromUiChange = draftBindings::updateCreateModuleHidden,
        onConfigTextChange = draftBindings::updateCreateModuleConfig,
        onRestoreTemplate = draftBindings::restoreTemplate,
        onCloseCreateModuleDialog = draftBindings::closeCreateModuleDialog,
        onCreateModule = draftBindings::createModule,
        onRefreshFromConfig = draftBindings::refreshFromConfig,
        onApplyFormState = draftBindings::applyFormState,
        onSelectSql = draftBindings::selectSql,
        onSqlChange = draftBindings::updateSql,
        onCreateSql = draftBindings::createSql,
        onRenameSql = draftBindings::renameSql,
        onDeleteSql = draftBindings::deleteSql,
        onConfigDraftChange = draftBindings::updateConfigDraft,
        onMetadataTitleChange = draftBindings::updateMetadataTitle,
        onMetadataDescriptionChange = draftBindings::updateMetadataDescription,
        onMetadataTagsChange = draftBindings::updateMetadataTags,
        onMetadataHiddenFromUiChange = draftBindings::updateMetadataHidden,
    )
}

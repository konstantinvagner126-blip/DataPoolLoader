package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun ModuleEditorPageContent(
    currentRoute: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    uiState: ModuleEditorPageUiState,
    callbacks: ModuleEditorPageCallbacks,
    session: ModuleEditorSessionResponse?,
    selectedSqlPath: String?,
    selectedModuleId: String?,
    hasRunningRun: Boolean,
    capabilities: ModuleLifecycleCapabilities?,
    actionBusy: Boolean,
) {
    state.successMessage?.let { AlertBanner(it, "success") }

    DatabaseModeAlert(currentRoute, state)

    Div({ classes("row", "g-4") }) {
        Div({ classes("col-12", "col-xl-3") }) {
            ModuleCatalogSidebar(
                route = currentRoute,
                state = state,
                capabilities = capabilities,
                actionBusy = actionBusy,
                onOpenCreateModule = callbacks.onOpenCreateModule,
                onDeleteModule = callbacks.onDeleteModule,
                onImportModules = { window.location.href = "/db-sync" },
                onToggleIncludeHidden = callbacks.onToggleIncludeHidden,
                onSelectModule = callbacks.onSelectModule,
            )
        }

        Div({ classes("col-12", "col-xl-9") }) {
            ModuleEditorContentPane(
                route = currentRoute,
                state = state,
                session = session,
                selectedSqlPath = selectedSqlPath,
                runPanelState = uiState.runPanelState,
                credentialsUploadInProgress = uiState.credentialsUploadInProgress,
                selectedCredentialsFile = uiState.selectedCredentialsFile,
                credentialsUploadMessage = uiState.credentialsUploadMessage,
                credentialsUploadMessageLevel = uiState.credentialsUploadMessageLevel,
                onCredentialsFileSelected = callbacks.onCredentialsFileSelected,
                onUploadCredentials = callbacks.onUploadCredentials,
                onSelectTab = callbacks.onSelectTab,
                onRun = callbacks.onRun,
                onSave = callbacks.onSave,
                onDiscardWorkingCopy = callbacks.onDiscardWorkingCopy,
                onPublishWorkingCopy = callbacks.onPublishWorkingCopy,
                onOpenCreateModule = callbacks.onOpenCreateModule,
                onDeleteModule = callbacks.onDeleteModule,
                onReload = callbacks.onReload,
                onModuleCodeChange = callbacks.onModuleCodeChange,
                onTitleChange = callbacks.onTitleChange,
                onDescriptionChange = callbacks.onDescriptionChange,
                onTagsChange = callbacks.onTagsChange,
                onHiddenFromUiChange = callbacks.onHiddenFromUiChange,
                onConfigTextChange = callbacks.onConfigTextChange,
                onRestoreTemplate = callbacks.onRestoreTemplate,
                onCloseCreateModuleDialog = callbacks.onCloseCreateModuleDialog,
                onCreateModule = callbacks.onCreateModule,
                onRefreshFromConfig = callbacks.onRefreshFromConfig,
                onApplyFormState = callbacks.onApplyFormState,
                onSelectSql = callbacks.onSelectSql,
                onSqlChange = callbacks.onSqlChange,
                onCreateSql = callbacks.onCreateSql,
                onRenameSql = callbacks.onRenameSql,
                onDeleteSql = callbacks.onDeleteSql,
                onConfigDraftChange = callbacks.onConfigDraftChange,
                onMetadataTitleChange = callbacks.onMetadataTitleChange,
                onMetadataDescriptionChange = callbacks.onMetadataDescriptionChange,
                onMetadataTagsChange = callbacks.onMetadataTagsChange,
                onMetadataHiddenFromUiChange = callbacks.onMetadataHiddenFromUiChange,
            )
        }
    }
}

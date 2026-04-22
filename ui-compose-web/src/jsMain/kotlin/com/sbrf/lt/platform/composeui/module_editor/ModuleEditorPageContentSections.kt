package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import kotlinx.browser.window
import org.w3c.files.File
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun ModuleEditorPageContent(
    currentRoute: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    session: ModuleEditorSessionResponse?,
    selectedSqlPath: String?,
    runPanelState: ModuleRunsPageState,
    selectedModuleId: String?,
    hasRunningRun: Boolean,
    capabilities: ModuleLifecycleCapabilities?,
    actionBusy: Boolean,
    credentialsUploadInProgress: Boolean,
    selectedCredentialsFile: File?,
    credentialsUploadMessage: String?,
    credentialsUploadMessageLevel: String,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
    onToggleIncludeHidden: () -> Unit,
    onSelectModule: (String) -> Unit,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
    onSelectTab: (ModuleEditorTab) -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onDiscardWorkingCopy: () -> Unit,
    onPublishWorkingCopy: () -> Unit,
    onReload: () -> Unit,
    onModuleCodeChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
    onConfigTextChange: (String) -> Unit,
    onRestoreTemplate: () -> Unit,
    onCloseCreateModuleDialog: () -> Unit,
    onCreateModule: () -> Unit,
    onRefreshFromConfig: () -> Unit,
    onApplyFormState: (ConfigFormStateDto) -> Unit,
    onSelectSql: (String) -> Unit,
    onSqlChange: (String, String) -> Unit,
    onCreateSql: () -> Unit,
    onRenameSql: () -> Unit,
    onDeleteSql: () -> Unit,
    onConfigDraftChange: (String) -> Unit,
    onMetadataTitleChange: (String) -> Unit,
    onMetadataDescriptionChange: (String) -> Unit,
    onMetadataTagsChange: (List<String>) -> Unit,
    onMetadataHiddenFromUiChange: (Boolean) -> Unit,
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
                onOpenCreateModule = onOpenCreateModule,
                onDeleteModule = onDeleteModule,
                onImportModules = { window.location.href = "/db-sync" },
                onToggleIncludeHidden = onToggleIncludeHidden,
                onSelectModule = onSelectModule,
            )
        }

        Div({ classes("col-12", "col-xl-9") }) {
            ModuleEditorContentPane(
                route = currentRoute,
                state = state,
                session = session,
                selectedSqlPath = selectedSqlPath,
                runPanelState = runPanelState,
                credentialsUploadInProgress = credentialsUploadInProgress,
                selectedCredentialsFile = selectedCredentialsFile,
                credentialsUploadMessage = credentialsUploadMessage,
                credentialsUploadMessageLevel = credentialsUploadMessageLevel,
                onCredentialsFileSelected = onCredentialsFileSelected,
                onUploadCredentials = onUploadCredentials,
                onSelectTab = onSelectTab,
                onRun = onRun,
                onSave = onSave,
                onDiscardWorkingCopy = onDiscardWorkingCopy,
                onPublishWorkingCopy = onPublishWorkingCopy,
                onOpenCreateModule = onOpenCreateModule,
                onDeleteModule = onDeleteModule,
                onReload = onReload,
                onModuleCodeChange = onModuleCodeChange,
                onTitleChange = onTitleChange,
                onDescriptionChange = onDescriptionChange,
                onTagsChange = onTagsChange,
                onHiddenFromUiChange = onHiddenFromUiChange,
                onConfigTextChange = onConfigTextChange,
                onRestoreTemplate = onRestoreTemplate,
                onCloseCreateModuleDialog = onCloseCreateModuleDialog,
                onCreateModule = onCreateModule,
                onRefreshFromConfig = onRefreshFromConfig,
                onApplyFormState = onApplyFormState,
                onSelectSql = onSelectSql,
                onSqlChange = onSqlChange,
                onCreateSql = onCreateSql,
                onRenameSql = onRenameSql,
                onDeleteSql = onDeleteSql,
                onConfigDraftChange = onConfigDraftChange,
                onMetadataTitleChange = onMetadataTitleChange,
                onMetadataDescriptionChange = onMetadataDescriptionChange,
                onMetadataTagsChange = onMetadataTagsChange,
                onMetadataHiddenFromUiChange = onMetadataHiddenFromUiChange,
            )
        }
    }
}

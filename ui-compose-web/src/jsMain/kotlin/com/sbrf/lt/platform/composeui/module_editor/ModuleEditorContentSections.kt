package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import org.w3c.files.File

@Composable
internal fun ModuleEditorContentPane(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    session: ModuleEditorSessionResponse?,
    selectedSqlPath: String?,
    runPanelState: ModuleRunsPageState,
    credentialsUploadInProgress: Boolean,
    selectedCredentialsFile: File?,
    credentialsUploadMessage: String?,
    credentialsUploadMessageLevel: String,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
    onSelectTab: (ModuleEditorTab) -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onDiscardWorkingCopy: () -> Unit,
    onPublishWorkingCopy: () -> Unit,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
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
    if (state.loading && session == null) {
        LoadingStateCard(
            title = "Редактор модуля",
            text = "Загружаю compose-shell выбранного модуля.",
        )
        return
    }
    if (session == null) {
        EmptyStateCard(
            title = "Редактор модуля",
            text = "Модуль не выбран или недоступен.",
        )
        return
    }

    CredentialsPanel(
        module = session.module,
        sectionStateKey = "module-editor.sections.${route.storage}.${session.module.id}.credentials",
        uploadInProgress = credentialsUploadInProgress,
        selectedFileName = selectedCredentialsFile?.name,
        uploadMessage = credentialsUploadMessage,
        uploadMessageLevel = credentialsUploadMessageLevel,
        onFileSelected = onCredentialsFileSelected,
        onUpload = onUploadCredentials,
    )

    EditorShellHeader(
        route = route,
        state = state,
        onTabSelect = onSelectTab,
        onRun = onRun,
        onSave = onSave,
        onDiscardWorkingCopy = onDiscardWorkingCopy,
        onPublishWorkingCopy = onPublishWorkingCopy,
        onOpenCreateModule = onOpenCreateModule,
        onDeleteModule = onDeleteModule,
        onReload = onReload,
    )
    ValidationAlert(session)

    state.selectedModuleId?.let {
        EditorRunOverviewPanel(
            route = route,
            state = runPanelState,
        )
    }

    if (route.storage == "database" && state.createModuleDialogOpen) {
        CreateModulePanel(
            state = state,
            onModuleCodeChange = onModuleCodeChange,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onTagsChange = onTagsChange,
            onHiddenFromUiChange = onHiddenFromUiChange,
            onConfigTextChange = onConfigTextChange,
            onRestoreTemplate = onRestoreTemplate,
            onCancel = onCloseCreateModuleDialog,
            onCreate = onCreateModule,
        )
    }

    TabNavigation(
        activeTab = state.activeTab,
        onTabSelect = onSelectTab,
    )

    when (state.activeTab) {
        ModuleEditorTab.SETTINGS -> ModuleEditorSettingsForm(
            storageMode = route.storage,
            state = state,
            module = session.module,
            onRefreshFromConfig = onRefreshFromConfig,
            onApplyFormState = onApplyFormState,
        )
        ModuleEditorTab.SQL -> SqlPreview(
            state = state,
            selectedSqlPath = selectedSqlPath,
            onSelectSql = onSelectSql,
            onSqlChange = onSqlChange,
            onCreateSql = onCreateSql,
            onRenameSql = onRenameSql,
            onDeleteSql = onDeleteSql,
        )
        ModuleEditorTab.CONFIG -> ConfigPreview(
            configText = state.configTextDraft,
            onConfigChange = onConfigDraftChange,
        )
        ModuleEditorTab.META -> MetadataForm(
            route = route,
            state = state,
            session = session,
            onTitleChange = onMetadataTitleChange,
            onDescriptionChange = onMetadataDescriptionChange,
            onTagsChange = onMetadataTagsChange,
            onHiddenFromUiChange = onMetadataHiddenFromUiChange,
        )
    }
}

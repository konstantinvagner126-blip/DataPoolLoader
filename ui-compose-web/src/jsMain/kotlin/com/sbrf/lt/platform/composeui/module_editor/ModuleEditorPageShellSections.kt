package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
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

@Composable
internal fun ModuleEditorNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) {
            Text(label)
        }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) {
        Text(label)
    }
}

@Composable
internal fun ModuleEditorHeroArt(storage: String) {
    if (storage == "database") {
        DatabaseModuleHeroArt()
    } else {
        FilesModuleHeroArt()
    }
}

@Composable
internal fun DatabaseModuleHeroArt() {
    Div({ classes("platform-stage") }) {
        Div({ classes("platform-node", "platform-node-db") }) { Text("POSTGRESQL") }
        Div({ classes("platform-node", "platform-node-kafka") }) { Text("REGISTRY") }
        Div({ classes("platform-node", "platform-node-pool") }) { Text("MODULES") }
        Div({ classes("platform-core") }) {
            Div({ classes("platform-core-title") }) { Text("DB") }
            Div({ classes("platform-core-subtitle") }) { Text("MODULE STORE") }
        }
        Div({ classes("platform-rail", "platform-rail-db") }) { Span({ classes("platform-packet", "packet-db") }) }
        Div({ classes("platform-rail", "platform-rail-kafka") }) { Span({ classes("platform-packet", "packet-kafka") }) }
        Div({ classes("platform-rail", "platform-rail-pool") }) { Span({ classes("platform-packet", "packet-pool") }) }
    }
}

@Composable
internal fun FilesModuleHeroArt() {
    Div({ classes("flow-stage") }) {
        listOf("DB1", "DB2", "DB3", "DB4", "DB5").forEachIndexed { index, label ->
            Div({ classes("source-node", "source-node-${index + 1}") }) { Text(label) }
        }
        repeat(5) { index ->
            Div({ classes("flow-line", "flow-line-${index + 1}") }) {
                Span({ classes("flow-dot", "dot-${index + 1}") })
            }
        }
        Div({ classes("merge-hub") }) {
            Div({ classes("merge-title") }) { Text("DATAPOOL") }
        }
    }
}

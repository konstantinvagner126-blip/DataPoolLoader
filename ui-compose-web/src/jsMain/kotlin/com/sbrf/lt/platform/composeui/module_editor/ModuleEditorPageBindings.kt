package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    fun updateState(transform: (ModuleEditorPageState) -> ModuleEditorPageState) {
        setState(transform(currentState()))
    }

    fun updateUiState(transform: (ModuleEditorPageUiState) -> ModuleEditorPageUiState) {
        setUiState(transform(currentUiState()))
    }

    fun openCreateModule() {
        updateState { store.openCreateModuleDialog(it) }
    }

    fun deleteModule() {
        val moduleId = currentState().selectedModuleId
        if (!moduleId.isNullOrBlank() && window.confirm("Удалить модуль '$moduleId'? Это действие необратимо.")) {
            scope.launch {
                val deletingState = store.beginAction(currentState(), "delete")
                val nextState = store.deleteDatabaseModule(deletingState, currentRoute())
                setState(nextState)
                setCurrentRoute(
                    currentRoute().copy(
                        moduleId = nextState.selectedModuleId,
                        openCreateDialog = false,
                    ),
                )
            }
        }
    }

    fun toggleIncludeHidden() {
        val nextRoute = currentRoute().copy(
            includeHidden = !currentRoute().includeHidden,
            openCreateDialog = false,
        )
        setCurrentRoute(nextRoute)
        scope.launch {
            setState(store.startLoading(currentState()))
            setState(store.load(nextRoute))
        }
    }

    fun selectModule(moduleId: String) {
        scope.launch {
            setState(store.startLoading(currentState()))
            setCurrentRoute(
                currentRoute().copy(
                    moduleId = moduleId,
                    openCreateDialog = false,
                ),
            )
            setState(store.selectModule(currentState(), currentRoute(), moduleId))
        }
    }

    fun uploadCredentials() {
        val file = currentUiState().selectedCredentialsFile ?: return
        val moduleId = currentState().selectedModuleId ?: return
        scope.launch {
            updateUiState {
                it.copy(
                    credentialsUploadInProgress = true,
                    credentialsUploadMessage = null,
                )
            }
            try {
                uploadCredentialsFile(credentialsHttpClient, file)
                setState(store.startLoading(currentState()))
                val refreshed = store.selectModule(currentState(), currentRoute(), moduleId)
                setState(
                    refreshed.copy(
                        successMessage = "Файл credential.properties загружен: ${file.name}.",
                    ),
                )
                updateUiState {
                    it.copy(
                        selectedCredentialsFile = null,
                        credentialsUploadMessage = "Статус credentials обновлен.",
                        credentialsUploadMessageLevel = "success",
                        credentialsUploadInProgress = false,
                    )
                }
            } catch (error: Throwable) {
                updateUiState {
                    it.copy(
                        credentialsUploadMessage = error.message ?: "Не удалось загрузить credential.properties.",
                        credentialsUploadMessageLevel = "warning",
                        credentialsUploadInProgress = false,
                    )
                }
            }
        }
    }

    fun runModule() {
        scope.launch {
            val runningState = store.beginAction(currentState(), "run")
            val nextState = if (currentRoute().storage == "database") {
                store.runDatabaseModule(runningState)
            } else {
                store.runFilesModule(runningState)
            }
            setState(nextState)
            refreshModuleCatalog()
            nextState.selectedModuleId?.let { moduleId ->
                refreshEditorRunPanel(moduleId)
            }
        }
    }

    fun saveModule() {
        scope.launch {
            val savingState = store.beginAction(currentState(), "save")
            val nextState = if (currentRoute().storage == "database") {
                store.saveDatabaseWorkingCopy(savingState, currentRoute())
            } else {
                store.saveFilesModule(savingState, currentRoute())
            }
            setState(nextState)
        }
    }

    fun discardWorkingCopy() {
        if (window.confirm("Сбросить личный черновик? Несохраненные изменения будут потеряны.")) {
            scope.launch {
                val discardState = store.beginAction(currentState(), "discard")
                setState(store.discardDatabaseWorkingCopy(discardState, currentRoute()))
            }
        }
    }

    fun publishWorkingCopy() {
        if (window.confirm("Опубликовать черновик как новую ревизию? После публикации личный черновик будет удален.")) {
            scope.launch {
                val publishState = store.beginAction(currentState(), "publish")
                setState(store.publishDatabaseWorkingCopy(publishState, currentRoute()))
            }
        }
    }

    fun reloadModule() {
        val moduleId = currentState().selectedModuleId
        if (!moduleId.isNullOrBlank()) {
            scope.launch {
                setState(store.startLoading(currentState()))
                setState(store.selectModule(currentState(), currentRoute(), moduleId))
            }
        }
    }

    fun createModule() {
        scope.launch {
            val creatingState = store.beginAction(currentState(), "create")
            val nextState = store.createDatabaseModule(creatingState, currentRoute())
            setState(nextState)
            setCurrentRoute(
                currentRoute().copy(
                    moduleId = nextState.selectedModuleId,
                    includeHidden = currentRoute().includeHidden || nextState.session?.module?.hiddenFromUi == true,
                    openCreateDialog = false,
                ),
            )
        }
    }

    fun refreshFromConfig() {
        scope.launch {
            val syncingState = store.startConfigFormSync(currentState())
            setState(store.syncConfigFormFromConfigDraft(syncingState))
        }
    }

    fun applyFormState(nextFormState: ConfigFormStateDto) {
        scope.launch {
            val syncingState = store.startConfigFormSync(currentState())
            setState(store.applyConfigForm(syncingState, nextFormState))
        }
    }

    fun createSql() {
        val rawName = window.prompt("Введите имя SQL-ресурса:")
        if (!rawName.isNullOrBlank()) {
            updateState { store.createSqlResource(it, rawName) }
        }
    }

    fun renameSql() {
        val currentPath = currentState().selectedSqlPath ?: return
        val rawName = window.prompt("Введите новое имя SQL-ресурса:", currentPath)
        if (!rawName.isNullOrBlank()) {
            scope.launch {
                setState(store.renameSqlResource(currentState(), rawName))
            }
        }
    }

    fun deleteSql() {
        val currentPath = currentState().selectedSqlPath ?: return
        if (window.confirm("Удалить SQL-ресурс '$currentPath'?")) {
            updateState { store.deleteSqlResource(it) }
        }
    }

    return ModuleEditorPageCallbacks(
        onOpenCreateModule = ::openCreateModule,
        onDeleteModule = ::deleteModule,
        onToggleIncludeHidden = ::toggleIncludeHidden,
        onSelectModule = ::selectModule,
        onCredentialsFileSelected = { file ->
            updateUiState {
                it.copy(
                    selectedCredentialsFile = file,
                    credentialsUploadMessage = null,
                )
            }
        },
        onUploadCredentials = ::uploadCredentials,
        onSelectTab = { tab -> updateState { store.selectTab(it, tab) } },
        onRun = ::runModule,
        onSave = ::saveModule,
        onDiscardWorkingCopy = ::discardWorkingCopy,
        onPublishWorkingCopy = ::publishWorkingCopy,
        onReload = ::reloadModule,
        onModuleCodeChange = { value -> updateState { store.updateCreateModuleCode(it, value) } },
        onTitleChange = { value -> updateState { store.updateCreateModuleTitle(it, value) } },
        onDescriptionChange = { value -> updateState { store.updateCreateModuleDescription(it, value) } },
        onTagsChange = { value -> updateState { store.updateCreateModuleTagsText(it, value) } },
        onHiddenFromUiChange = { value -> updateState { store.updateCreateModuleHiddenFromUi(it, value) } },
        onConfigTextChange = { value -> updateState { store.updateCreateModuleConfigText(it, value) } },
        onRestoreTemplate = { updateState { store.restoreCreateModuleTemplate(it) } },
        onCloseCreateModuleDialog = { updateState { store.closeCreateModuleDialog(it) } },
        onCreateModule = ::createModule,
        onRefreshFromConfig = ::refreshFromConfig,
        onApplyFormState = ::applyFormState,
        onSelectSql = { path -> updateState { store.selectSqlResource(it, path) } },
        onSqlChange = { path, value -> updateState { store.updateSqlText(it, path, value) } },
        onCreateSql = ::createSql,
        onRenameSql = ::renameSql,
        onDeleteSql = ::deleteSql,
        onConfigDraftChange = { value -> updateState { store.updateConfigText(it, value) } },
        onMetadataTitleChange = { value -> updateState { store.updateMetadataTitle(it, value) } },
        onMetadataDescriptionChange = { value -> updateState { store.updateMetadataDescription(it, value) } },
        onMetadataTagsChange = { value -> updateState { store.updateMetadataTags(it, value) } },
        onMetadataHiddenFromUiChange = { value -> updateState { store.updateMetadataHiddenFromUi(it, value) } },
    )
}

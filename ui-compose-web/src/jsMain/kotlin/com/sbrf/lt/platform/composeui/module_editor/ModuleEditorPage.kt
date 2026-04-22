package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.foundation.updates.WebSocketEffect
import com.sbrf.lt.platform.composeui.foundation.updates.buildWebSocketUrl
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsApiClient
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsRouteState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsStore

@Composable
fun ComposeModuleEditorPage(
    route: ModuleEditorRouteState,
    api: ModuleEditorApi = remember { ModuleEditorApiClient() },
) {
    var currentRoute by remember(route.storage, route.moduleId, route.includeHidden, route.openCreateDialog) { mutableStateOf(route) }
    val store = remember(api) {
        ModuleEditorStore(
            api = api,
            syncRoute = { storage, moduleId, includeHidden ->
                window.history.replaceState(null, "", buildPrimaryEditorUrl(storage, moduleId, includeHidden))
            },
        )
    }
    val credentialsHttpClient = remember { ComposeHttpClient() }
    val runsApi = remember { ModuleRunsApiClient() }
    val runsStore = remember(runsApi) { ModuleRunsStore(runsApi) }
    val scope = rememberCoroutineScope()
    var state by remember(route.storage, route.moduleId, route.includeHidden, route.openCreateDialog) { mutableStateOf(ModuleEditorPageState()) }
    var runPanelState by remember(route.storage, route.moduleId) {
        mutableStateOf(ModuleRunsPageState(loading = false, historyLimit = 3))
    }
    var runPanelRefreshInProgress by remember(route.storage, route.moduleId) { mutableStateOf(false) }
    var selectedCredentialsFile by remember(route.storage, route.moduleId) { mutableStateOf<File?>(null) }
    var credentialsUploadMessage by remember(route.storage, route.moduleId) { mutableStateOf<String?>(null) }
    var credentialsUploadMessageLevel by remember(route.storage, route.moduleId) { mutableStateOf("success") }
    var credentialsUploadInProgress by remember(route.storage, route.moduleId) { mutableStateOf(false) }

    suspend fun refreshEditorRunPanel(moduleId: String) {
        if (runPanelRefreshInProgress) {
            return
        }
        runPanelRefreshInProgress = true
        try {
            val routeState = ModuleRunsRouteState(currentRoute.storage, moduleId)
            runPanelState = if (runPanelState.history == null) {
                runsStore.load(route = routeState, historyLimit = runPanelState.historyLimit)
            } else {
                runsStore.reloadHistory(current = runPanelState, route = routeState, preferActiveRun = true)
            }
        } finally {
            runPanelRefreshInProgress = false
        }
    }

    suspend fun refreshModuleCatalog() {
        state = store.refreshCatalog(state, currentRoute)
    }

    LaunchedEffect(currentRoute.storage, currentRoute.moduleId, currentRoute.includeHidden, currentRoute.openCreateDialog) {
        state = store.startLoading(state)
        val loadedState = store.load(currentRoute)
        state = if (currentRoute.storage == "database" && currentRoute.openCreateDialog) {
            window.history.replaceState(
                null,
                "",
                buildComposeEditorUrl(
                    storage = currentRoute.storage,
                    moduleId = currentRoute.moduleId,
                    includeHidden = currentRoute.includeHidden,
                ),
            )
            store.openCreateModuleDialog(loadedState)
        } else {
            loadedState
        }
    }

    LaunchedEffect(state.successMessage) {
        if (!state.successMessage.isNullOrBlank()) {
            delay(5_000)
            state = store.clearSuccessMessage(state)
        }
    }

    LaunchedEffect(currentRoute.storage, state.selectedModuleId) {
        val moduleId = state.selectedModuleId
        if (moduleId.isNullOrBlank()) {
            runPanelState = ModuleRunsPageState(loading = false, historyLimit = 3)
        } else {
            val loadingState = ModuleRunsPageState(loading = true, historyLimit = 3)
            runPanelState = runsStore.startLoading(loadingState)
            runPanelState = runsStore.load(
                route = ModuleRunsRouteState(currentRoute.storage, moduleId),
                historyLimit = 3,
            )
        }
    }

    val session = state.session
    val selectedSql = session?.module?.sqlFiles?.firstOrNull { it.path == state.selectedSqlPath }
        ?: session?.module?.sqlFiles?.firstOrNull()
    val selectedModuleId = state.selectedModuleId
    val hasRunningRun = runPanelState.history?.runs?.any { it.status.equals("RUNNING", ignoreCase = true) } == true
    val capabilities = session?.capabilities
    val actionBusy = state.actionInProgress != null

    val onRunAction: () -> Unit = {
        scope.launch {
            state = store.beginAction(state, "run")
            state = if (currentRoute.storage == "database") {
                store.runDatabaseModule(state)
            } else {
                store.runFilesModule(state)
            }
            refreshModuleCatalog()
            state.selectedModuleId?.let { moduleId ->
                refreshEditorRunPanel(moduleId)
            }
        }
    }
    val onSaveAction: () -> Unit = {
        scope.launch {
            state = store.beginAction(state, "save")
            state = if (currentRoute.storage == "database") {
                store.saveDatabaseWorkingCopy(state, currentRoute)
            } else {
                store.saveFilesModule(state, currentRoute)
            }
        }
    }
    val onDiscardWorkingCopyAction: () -> Unit = {
        if (window.confirm("Сбросить личный черновик? Несохраненные изменения будут потеряны.")) {
            scope.launch {
                state = store.beginAction(state, "discard")
                state = store.discardDatabaseWorkingCopy(state, currentRoute)
            }
        }
    }
    val onPublishWorkingCopyAction: () -> Unit = {
        if (window.confirm("Опубликовать черновик как новую ревизию? После публикации личный черновик будет удален.")) {
            scope.launch {
                state = store.beginAction(state, "publish")
                state = store.publishDatabaseWorkingCopy(state, currentRoute)
            }
        }
    }
    val onOpenCreateModuleAction: () -> Unit = {
        state = store.openCreateModuleDialog(state)
    }
    val onDeleteModuleAction: () -> Unit = {
        val moduleId = state.selectedModuleId
        if (!moduleId.isNullOrBlank() && window.confirm("Удалить модуль '$moduleId'? Это действие необратимо.")) {
            scope.launch {
                state = store.beginAction(state, "delete")
                state = store.deleteDatabaseModule(state, currentRoute)
                currentRoute = currentRoute.copy(
                    moduleId = state.selectedModuleId,
                    openCreateDialog = false,
                )
            }
        }
    }
    val onReloadAction: () -> Unit = {
        val moduleId = state.selectedModuleId
        if (!moduleId.isNullOrBlank()) {
            scope.launch {
                state = store.startLoading(state)
                state = store.selectModule(state, currentRoute, moduleId)
            }
        }
    }

    PollingEffect(
        enabled = currentRoute.storage == "database" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            val moduleId = selectedModuleId ?: return@PollingEffect
            if (runPanelRefreshInProgress) {
                return@PollingEffect
            }
            runPanelRefreshInProgress = true
            try {
                runPanelState = runsStore.reloadHistory(
                    current = runPanelState,
                    route = ModuleRunsRouteState(currentRoute.storage, moduleId),
                    preferActiveRun = true,
                )
            } finally {
                runPanelRefreshInProgress = false
            }
        },
    )

    PollingEffect(
        enabled = currentRoute.storage == "files" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            val moduleId = selectedModuleId ?: return@PollingEffect
            if (runPanelRefreshInProgress) {
                return@PollingEffect
            }
            runPanelRefreshInProgress = true
            try {
                runPanelState = runsStore.reloadHistory(
                    current = runPanelState,
                    route = ModuleRunsRouteState(currentRoute.storage, moduleId),
                    preferActiveRun = true,
                )
                refreshModuleCatalog()
            } finally {
                runPanelRefreshInProgress = false
            }
        },
    )

    PollingEffect(
        enabled = currentRoute.storage == "database" && !state.loading && state.modules.isNotEmpty(),
        intervalMs = 3000,
        onTick = {
            refreshModuleCatalog()
        },
    )

    WebSocketEffect(
        enabled = currentRoute.storage == "files" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading,
        url = buildWebSocketUrl(),
        onMessage = {
            val moduleId = selectedModuleId ?: return@WebSocketEffect
            if (runPanelRefreshInProgress) {
                return@WebSocketEffect
            }
            runPanelRefreshInProgress = true
            try {
                runPanelState = runsStore.reloadHistory(
                    current = runPanelState,
                    route = ModuleRunsRouteState(currentRoute.storage, moduleId),
                    preferActiveRun = true,
                )
                refreshModuleCatalog()
            } finally {
                runPanelRefreshInProgress = false
            }
        },
    )

    if (state.errorMessage != null) {
        EditorErrorMessageBox(
            message = state.errorMessage ?: "",
            onDismiss = { state = store.clearErrorMessage(state) },
        )
    }

    PageScaffold(
        eyebrow = if (currentRoute.storage == "database") "Режим базы данных" else "DataPool Loader",
        title = if (currentRoute.storage == "database") "Модули из базы данных" else "Управление пулами данных НТ",
        subtitle = if (currentRoute.storage == "database") {
            "Просмотр, редактирование и запуск модулей из PostgreSQL. Личный черновик, публикация изменений и история запусков."
        } else {
            "Выбери модуль, поправь YAML и SQL, затем запусти выгрузку прямо из браузера."
        },
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                ModuleEditorNavActionButton("На главную", hrefValue = "/")
                ModuleEditorNavActionButton(
                    if (currentRoute.storage == "database") "Модули из базы данных" else "Модули",
                    active = true,
                )
            }
        },
        content = {
            ModuleEditorPageContent(
                currentRoute = currentRoute,
                state = state,
                session = session,
                selectedSqlPath = selectedSql?.path,
                runPanelState = runPanelState,
                selectedModuleId = selectedModuleId,
                hasRunningRun = hasRunningRun,
                capabilities = capabilities,
                actionBusy = actionBusy,
                credentialsUploadInProgress = credentialsUploadInProgress,
                selectedCredentialsFile = selectedCredentialsFile,
                credentialsUploadMessage = credentialsUploadMessage,
                credentialsUploadMessageLevel = credentialsUploadMessageLevel,
                onOpenCreateModule = onOpenCreateModuleAction,
                onDeleteModule = onDeleteModuleAction,
                onToggleIncludeHidden = {
                    val nextRoute = currentRoute.copy(
                        includeHidden = !currentRoute.includeHidden,
                        openCreateDialog = false,
                    )
                    currentRoute = nextRoute
                    scope.launch {
                        state = store.startLoading(state)
                        state = store.load(nextRoute)
                    }
                },
                onSelectModule = { moduleId ->
                    scope.launch {
                        state = store.startLoading(state)
                        currentRoute = currentRoute.copy(
                            moduleId = moduleId,
                            openCreateDialog = false,
                        )
                        state = store.selectModule(state, currentRoute, moduleId)
                    }
                },
                onCredentialsFileSelected = { file ->
                    selectedCredentialsFile = file
                    credentialsUploadMessage = null
                },
                onUploadCredentials = {
                    val file = selectedCredentialsFile ?: return@ModuleEditorPageContent
                    val moduleId = state.selectedModuleId ?: return@ModuleEditorPageContent
                    scope.launch {
                        credentialsUploadInProgress = true
                        credentialsUploadMessage = null
                        try {
                            uploadCredentialsFile(credentialsHttpClient, file)
                            state = store.startLoading(state)
                            val refreshed = store.selectModule(state, currentRoute, moduleId)
                            state = refreshed.copy(
                                successMessage = "Файл credential.properties загружен: ${file.name}.",
                            )
                            selectedCredentialsFile = null
                            credentialsUploadMessageLevel = "success"
                            credentialsUploadMessage = "Статус credentials обновлен."
                        } catch (error: Throwable) {
                            credentialsUploadMessageLevel = "warning"
                            credentialsUploadMessage = error.message ?: "Не удалось загрузить credential.properties."
                        } finally {
                            credentialsUploadInProgress = false
                        }
                    }
                },
                onSelectTab = { tab -> state = store.selectTab(state, tab) },
                onRun = onRunAction,
                onSave = onSaveAction,
                onDiscardWorkingCopy = onDiscardWorkingCopyAction,
                onPublishWorkingCopy = onPublishWorkingCopyAction,
                onReload = onReloadAction,
                onModuleCodeChange = { value -> state = store.updateCreateModuleCode(state, value) },
                onTitleChange = { value -> state = store.updateCreateModuleTitle(state, value) },
                onDescriptionChange = { value -> state = store.updateCreateModuleDescription(state, value) },
                onTagsChange = { value -> state = store.updateCreateModuleTagsText(state, value) },
                onHiddenFromUiChange = { value -> state = store.updateCreateModuleHiddenFromUi(state, value) },
                onConfigTextChange = { value -> state = store.updateCreateModuleConfigText(state, value) },
                onRestoreTemplate = { state = store.restoreCreateModuleTemplate(state) },
                onCloseCreateModuleDialog = { state = store.closeCreateModuleDialog(state) },
                onCreateModule = {
                    scope.launch {
                        state = store.beginAction(state, "create")
                        state = store.createDatabaseModule(state, currentRoute)
                        currentRoute = currentRoute.copy(
                            moduleId = state.selectedModuleId,
                            includeHidden = currentRoute.includeHidden || state.session?.module?.hiddenFromUi == true,
                            openCreateDialog = false,
                        )
                    }
                },
                onRefreshFromConfig = {
                    scope.launch {
                        state = store.startConfigFormSync(state)
                        state = store.syncConfigFormFromConfigDraft(state)
                    }
                },
                onApplyFormState = { nextFormState ->
                    scope.launch {
                        state = store.startConfigFormSync(state)
                        state = store.applyConfigForm(state, nextFormState)
                    }
                },
                onSelectSql = { path -> state = store.selectSqlResource(state, path) },
                onSqlChange = { path, value -> state = store.updateSqlText(state, path, value) },
                onCreateSql = {
                    val rawName = window.prompt("Введите имя SQL-ресурса:")
                    if (!rawName.isNullOrBlank()) {
                        state = store.createSqlResource(state, rawName)
                    }
                },
                onRenameSql = {
                    val currentPath = state.selectedSqlPath ?: return@ModuleEditorPageContent
                    val rawName = window.prompt("Введите новое имя SQL-ресурса:", currentPath)
                    if (!rawName.isNullOrBlank()) {
                        scope.launch {
                            state = store.renameSqlResource(state, rawName)
                        }
                    }
                },
                onDeleteSql = {
                    val currentPath = state.selectedSqlPath ?: return@ModuleEditorPageContent
                    if (window.confirm("Удалить SQL-ресурс '$currentPath'?")) {
                        state = store.deleteSqlResource(state)
                    }
                },
                onConfigDraftChange = { value -> state = store.updateConfigText(state, value) },
                onMetadataTitleChange = { value -> state = store.updateMetadataTitle(state, value) },
                onMetadataDescriptionChange = { value -> state = store.updateMetadataDescription(state, value) },
                onMetadataTagsChange = { value -> state = store.updateMetadataTags(state, value) },
                onMetadataHiddenFromUiChange = { value -> state = store.updateMetadataHiddenFromUi(state, value) },
            )
        },
        heroArt = {
            ModuleEditorHeroArt(currentRoute.storage)
        },
    )
}

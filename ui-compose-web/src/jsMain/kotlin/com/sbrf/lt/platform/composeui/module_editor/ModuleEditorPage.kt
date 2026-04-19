package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressMetric
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressWidget
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.buildRunProgressStages
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.foundation.updates.WebSocketEffect
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.module_editor.buildCatalogStatus
import com.sbrf.lt.platform.composeui.module_editor.buildCredentialsStatusText
import com.sbrf.lt.platform.composeui.module_editor.buildCredentialsWarningText
import com.sbrf.lt.platform.composeui.module_editor.buildDraftStatusText
import com.sbrf.lt.platform.composeui.module_editor.translateSourceKind
import com.sbrf.lt.platform.composeui.module_editor.translateValidationStatus
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.xhr.FormData
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunDetailsResponse
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsApiClient
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsRouteState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsStore
import com.sbrf.lt.platform.composeui.module_runs.buildCompactProgressEntries
import com.sbrf.lt.platform.composeui.module_runs.detectActiveSourceName
import com.sbrf.lt.platform.composeui.module_runs.detectRunStageKey
import com.sbrf.lt.platform.composeui.module_runs.eventEntryCssClass
import com.sbrf.lt.platform.composeui.module_runs.parseStructuredRunSummary
import com.sbrf.lt.platform.composeui.module_runs.runStatusCssClass
import com.sbrf.lt.platform.composeui.module_runs.translateRunStatus
import com.sbrf.lt.platform.composeui.module_runs.translateStage
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

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
                val query = buildString {
                    var separator = '?'
                    if (!moduleId.isNullOrBlank()) {
                        append(separator)
                        append("module=")
                        append(moduleId)
                        separator = '&'
                    }
                    if (storage == "database" && includeHidden) {
                        append(separator)
                        append("includeHidden=true")
                    }
                }
                window.history.replaceState(null, "", buildPrimaryEditorUrl(storage, query))
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
        url = buildEditorWebSocketUrl(),
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
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) {
                    Text(if (currentRoute.storage == "database") "Модули из базы данных" else "Модули")
                }
            }
        },
        content = {
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }

            DatabaseModeAlert(currentRoute, state)

            Div({ classes("row", "g-4") }) {
                Div({ classes("col-12", "col-xl-3") }) {
                    Div({ classes("panel", "h-100") }) {
                        Div({ classes("panel-title") }) {
                            Text(if (currentRoute.storage == "database") "Модули из базы данных" else "Модули")
                        }
                        Div({
                            classes("module-catalog-status", "mt-3", "mb-3", "text-secondary", "small")
                        }) {
                            Text(buildCatalogStatus(state, currentRoute.storage))
                        }
                        if (currentRoute.storage == "database") {
                            Div({ classes("module-catalog-actions", "mb-3") }) {
                                EditorIconActionButton(
                                    icon = "+",
                                    label = "Новый",
                                    title = "Новый модуль",
                                    enabled = capabilities?.createModule == true && !actionBusy,
                                    style = EditorActionStyle.PrimaryOutline,
                                    onClick = onOpenCreateModuleAction,
                                )
                                EditorIconActionButton(
                                    icon = "−",
                                    label = "Удалить",
                                    title = "Удалить модуль",
                                    enabled = capabilities?.deleteModule == true && !actionBusy && state.selectedModuleId != null,
                                    style = EditorActionStyle.DangerOutline,
                                    onClick = onDeleteModuleAction,
                                )
                                EditorIconActionButton(
                                    icon = "⇅",
                                    label = "Импорт",
                                    title = "Импорт из файлов",
                                    enabled = true,
                                    style = EditorActionStyle.SecondaryOutline,
                                    onClick = { window.location.href = "/db-sync" },
                                )
                            }
                        }
                        if (currentRoute.storage == "database") {
                            Label(attrs = { classes("config-form-check", "mb-3") }) {
                                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                                    classes("form-check-input")
                                    if (currentRoute.includeHidden) {
                                        attr("checked", "checked")
                                    }
                                    onClick {
                                        val nextRoute = currentRoute.copy(
                                            includeHidden = !currentRoute.includeHidden,
                                            openCreateDialog = false,
                                        )
                                        currentRoute = nextRoute
                                        scope.launch {
                                            state = store.startLoading(state)
                                            state = store.load(nextRoute)
                                        }
                                    }
                                })
                                Span({ classes("form-check-label") }) { Text("Показывать скрытые модули") }
                            }
                        }
                        if (currentRoute.storage == "database" && currentRoute.includeHidden) {
                            Div({ classes("text-secondary", "small", "mb-3") }) {
                                Text("Каталог открыт с показом скрытых модулей.")
                            }
                        }
                        if (state.loading && state.modules.isEmpty()) {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Каталог модулей загружается.")
                            }
                        } else {
                            Div({ classes("list-group", "module-list") }) {
                                state.modules.forEach { module ->
                                    Button(attrs = {
                                        classes("list-group-item", "list-group-item-action")
                                        if (module.id == state.selectedModuleId) {
                                            classes("active")
                                        }
                                        attr("type", "button")
                                        onClick {
                                        scope.launch {
                                            state = store.startLoading(state)
                                            currentRoute = currentRoute.copy(
                                                moduleId = module.id,
                                                    openCreateDialog = false,
                                                )
                                                state = store.selectModule(state, currentRoute, module.id)
                                            }
                                        }
                                    }) {
                                        val moduleDescription = module.description
                                        Div({ classes("module-list-head") }) {
                                            Div {
                                                Div({ classes("module-list-title") }) {
                                                    Text(module.title.ifBlank { module.id })
                                                }
                                                if (!moduleDescription.isNullOrBlank()) {
                                                    Div({ classes("module-list-description") }) {
                                                        Text(moduleDescription)
                                                    }
                                                }
                                            }
                                            Span({
                                                classes("module-validation-badge", validationBadgeClass(module.validationStatus))
                                            }) {
                                                Text(translateValidationStatus(module.validationStatus))
                                            }
                                            if (module.hasActiveRun) {
                                                ModuleRunningBadge()
                                            }
                                            if (module.hiddenFromUi) {
                                                Span({ classes("module-validation-badge", "module-validation-badge-warning") }) {
                                                    Text("Скрыт")
                                                }
                                            }
                                        }
                                        if (module.tags.isNotEmpty()) {
                                            Div({ classes("module-list-tags") }) {
                                                module.tags.forEach { tag ->
                                                    Span({ classes("module-tag") }) { Text(tag) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Div({ classes("col-12", "col-xl-9") }) {
                    if (state.loading && session == null) {
                        LoadingStateCard(
                            title = "Редактор модуля",
                            text = "Загружаю compose-shell выбранного модуля.",
                        )
                    } else if (session == null) {
                        EmptyStateCard(
                            title = "Редактор модуля",
                            text = "Модуль не выбран или недоступен.",
                        )
                    } else {
                        CredentialsPanel(
                            module = session.module,
                            sectionStateKey = "module-editor.sections.${currentRoute.storage}.${session.module.id}.credentials",
                            uploadInProgress = credentialsUploadInProgress,
                            selectedFileName = selectedCredentialsFile?.name,
                            uploadMessage = credentialsUploadMessage,
                            uploadMessageLevel = credentialsUploadMessageLevel,
                            onFileSelected = { file ->
                                selectedCredentialsFile = file
                                credentialsUploadMessage = null
                            },
                            onUpload = {
                                val file = selectedCredentialsFile ?: return@CredentialsPanel
                                val moduleId = state.selectedModuleId ?: return@CredentialsPanel
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
                        )

                        EditorShellHeader(
                            route = currentRoute,
                            state = state,
                            onTabSelect = { tab -> state = store.selectTab(state, tab) },
                            onRun = onRunAction,
                            onSave = onSaveAction,
                            onDiscardWorkingCopy = onDiscardWorkingCopyAction,
                            onPublishWorkingCopy = onPublishWorkingCopyAction,
                            onOpenCreateModule = onOpenCreateModuleAction,
                            onDeleteModule = onDeleteModuleAction,
                            onReload = onReloadAction,
                        )
                        ValidationAlert(session)

                        val currentModuleId = state.selectedModuleId
                        if (currentModuleId != null) {
                            EditorRunOverviewPanel(
                                route = currentRoute,
                                state = runPanelState,
                            )
                        }

                        if (currentRoute.storage == "database" && state.createModuleDialogOpen) {
                            CreateModulePanel(
                                state = state,
                                onModuleCodeChange = { value -> state = store.updateCreateModuleCode(state, value) },
                                onTitleChange = { value -> state = store.updateCreateModuleTitle(state, value) },
                                onDescriptionChange = { value -> state = store.updateCreateModuleDescription(state, value) },
                                onTagsChange = { value -> state = store.updateCreateModuleTagsText(state, value) },
                                onHiddenFromUiChange = { value -> state = store.updateCreateModuleHiddenFromUi(state, value) },
                                onConfigTextChange = { value -> state = store.updateCreateModuleConfigText(state, value) },
                                onRestoreTemplate = { state = store.restoreCreateModuleTemplate(state) },
                                onCancel = { state = store.closeCreateModuleDialog(state) },
                                onCreate = {
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
                            )
                        }

                        TabNavigation(
                            activeTab = state.activeTab,
                            onTabSelect = { tab -> state = store.selectTab(state, tab) },
                        )

                        when (state.activeTab) {
                            ModuleEditorTab.SETTINGS -> ModuleEditorSettingsForm(
                                storageMode = currentRoute.storage,
                                state = state,
                                module = session.module,
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
                            )
                            ModuleEditorTab.SQL -> SqlPreview(
                                state = state,
                                selectedSqlPath = selectedSql?.path,
                                onSelectSql = { path -> state = store.selectSqlResource(state, path) },
                                onSqlChange = { path, value -> state = store.updateSqlText(state, path, value) },
                                onCreateSql = {
                                    val rawName = window.prompt("Введите имя SQL-ресурса:")
                                    if (!rawName.isNullOrBlank()) {
                                        state = store.createSqlResource(state, rawName)
                                    }
                                },
                                onRenameSql = {
                                    val currentPath = state.selectedSqlPath ?: return@SqlPreview
                                    val rawName = window.prompt("Введите новое имя SQL-ресурса:", currentPath)
                                    if (!rawName.isNullOrBlank()) {
                                        scope.launch {
                                            state = store.renameSqlResource(state, rawName)
                                        }
                                    }
                                },
                                onDeleteSql = {
                                    val currentPath = state.selectedSqlPath ?: return@SqlPreview
                                    if (window.confirm("Удалить SQL-ресурс '$currentPath'?")) {
                                        state = store.deleteSqlResource(state)
                                    }
                                },
                            )
                            ModuleEditorTab.CONFIG -> ConfigPreview(
                                configText = state.configTextDraft,
                                onConfigChange = { value -> state = store.updateConfigText(state, value) },
                            )
                            ModuleEditorTab.META -> MetadataForm(
                                route = currentRoute,
                                state = state,
                                session = session,
                                onTitleChange = { value -> state = store.updateMetadataTitle(state, value) },
                                onDescriptionChange = { value -> state = store.updateMetadataDescription(state, value) },
                                onTagsChange = { value -> state = store.updateMetadataTags(state, value) },
                                onHiddenFromUiChange = { value -> state = store.updateMetadataHiddenFromUi(state, value) },
                            )
                        }
                    }
                }
            }
        },
        heroArt = {
            if (currentRoute.storage == "database") {
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
            } else {
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
        },
    )
}

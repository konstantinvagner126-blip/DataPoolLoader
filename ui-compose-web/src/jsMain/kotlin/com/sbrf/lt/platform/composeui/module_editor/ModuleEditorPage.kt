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

@Composable
private fun ModuleRunningBadge() {
    Span({ classes("module-running-badge") }) {
        Span({
            classes("module-running-badge-spinner")
            attr("aria-hidden", "true")
        })
        Span({
            classes("module-running-badge-arrows")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("module-running-badge-arrow", "module-running-badge-arrow-forward") }) {
                Text("↻")
            }
            Span({ classes("module-running-badge-arrow", "module-running-badge-arrow-backward") }) {
                Text("↺")
            }
        }
        Text("Выполняется")
    }
}

@Composable
private fun EditorErrorMessageBox(
    message: String,
    onDismiss: () -> Unit,
) {
    Div({ classes("editor-message-box") }) {
        Div({ classes("editor-message-box-head") }) {
            Div({ classes("editor-message-box-title") }) { Text("Операция не выполнена") }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                onClick { onDismiss() }
            }) {
                Text("Закрыть")
            }
        }
        Div({ classes("editor-message-box-text") }) {
            Text(message)
        }
    }
}

@Composable
private fun DatabaseModeAlert(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
) {
    if (route.storage != "database") {
        return
    }
    val runtimeContext = state.databaseCatalog?.runtimeContext ?: return
    if (runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) {
        return
    }

    Div({ classes("alert", "alert-warning", "mb-4") }) {
        Div({ classes("fw-semibold", "mb-1") }) {
            Text("Режим базы данных недоступен")
        }
        Text(
            runtimeContext.fallbackReason
                ?: "Для работы с модулями из базы данных нужно переключить режим на «База данных» и убедиться, что PostgreSQL доступен.",
        )
    }
}

@Composable
private fun EditorShellHeader(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    onTabSelect: (ModuleEditorTab) -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onDiscardWorkingCopy: () -> Unit,
    onPublishWorkingCopy: () -> Unit,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
    onReload: () -> Unit,
) {
    val session = requireNotNull(state.session)
    val module = session.module
    val capabilities = session.capabilities
    val actionBusy = state.actionInProgress != null
    val moduleDescription = module.description

    Div({ classes("panel", "mb-4") }) {
        Div({ classes("module-editor-header-shell") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("Редактор модуля") }
                Div({ classes("text-secondary", "small") }) { Text(module.title.ifBlank { module.id }) }
                if (!moduleDescription.isNullOrBlank()) {
                    Div({ classes("text-secondary", "small", "mt-1") }) { Text(moduleDescription) }
                }
                Div({ classes("module-draft-status", "small", "mt-1", "text-secondary") }) {
                    Span({
                        classes(
                            "module-draft-dot",
                            when {
                                state.hasDraftChanges -> "module-draft-dot-dirty"
                                route.storage == "database" && !session.workingCopyId.isNullOrBlank() -> "module-draft-dot-neutral"
                                else -> "module-draft-dot-saved"
                            },
                        )
                        attr("aria-hidden", "true")
                    })
                    Span {
                        Text(buildDraftStatusText(route, session, state.hasDraftChanges))
                    }
                }
                if (route.storage == "database" && !session.sourceKind.isNullOrBlank()) {
                    Div({ classes("small", "mt-1", "text-secondary") }) {
                        Text("Источник данных редактора: ${translateSourceKind(session.sourceKind)}")
                    }
                }
            }

            if (route.storage == "database") {
                Div({ classes("module-editor-toolbar") }) {
                    Div({ classes("module-editor-toolbar-row") }) {
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-primary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Выполнение") }
                            EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.Success, onRun)
                            A(attrs = {
                                classes("btn", "btn-outline-secondary")
                                if (state.selectedModuleId == null) {
                                    classes("disabled")
                                }
                                if (state.selectedModuleId != null) {
                                    href(buildRunsHref(route, state.selectedModuleId))
                                } else {
                                    attr("aria-disabled", "true")
                                }
                            }) { Text("История и результаты") }
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-draft") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Личный черновик") }
                            EditorActionButton(
                                "Сохранить черновик",
                                capabilities.saveWorkingCopy && state.hasDraftChanges && !actionBusy,
                                EditorActionStyle.PrimarySolid,
                                onSave,
                            )
                            EditorActionButton("Опубликовать", capabilities.publish && !actionBusy && !state.hasDraftChanges, EditorActionStyle.Success, onPublishWorkingCopy)
                            EditorActionButton("Сбросить черновик", capabilities.discardWorkingCopy && !actionBusy, EditorActionStyle.DangerOutline, onDiscardWorkingCopy)
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Редактор") }
                            EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                        }
                    }
                }
            } else {
                Div({ classes("module-editor-toolbar") }) {
                    Div({ classes("module-editor-toolbar-row") }) {
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-primary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Выполнение") }
                            A(attrs = {
                                classes("btn", "btn-outline-secondary")
                                if (state.selectedModuleId == null) {
                                    classes("disabled")
                                }
                                if (state.selectedModuleId != null) {
                                    href(buildRunsHref(route, state.selectedModuleId))
                                } else {
                                    attr("aria-disabled", "true")
                                }
                            }) { Text("История и результаты") }
                            EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.PrimarySolid, onRun)
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Редактор") }
                            EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                            EditorActionButton("Сохранить", capabilities.save && state.hasDraftChanges && !actionBusy, EditorActionStyle.PrimarySolid, onSave)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorRunOverviewPanel(
    route: ModuleEditorRouteState,
    state: ModuleRunsPageState,
) {
    SectionCard(
        title = "Ход выполнения",
        subtitle = "Компактный live-блок по текущему или последнему запуску. Полные детали остаются на отдельном экране.",
    ) {
        val history = state.history
        if (state.errorMessage != null) {
            AlertBanner(state.errorMessage ?: "", "warning")
        }

        when {
            state.loading && history == null -> {
                Div({ classes("text-secondary", "small") }) {
                    Text("Загружаю информацию о запусках модуля.")
                }
            }

            history == null || history.runs.isEmpty() || state.selectedRunDetails == null -> {
                Div({ classes("text-secondary", "small") }) {
                    Text("Активного запуска сейчас нет. Детали прошлых запусков открываются на отдельном экране.")
                }
            }

            else -> {
                val details = requireNotNull(state.selectedRunDetails)
                val structuredSummary = details.summaryJson?.let(::parseStructuredRunSummary)
                val currentStageKey = detectRunStageKey(details.run, details.events)
                val successCount = details.run.successfulSourceCount
                    ?: details.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) }
                val failedCount = details.run.failedSourceCount
                    ?: details.sourceResults.count { it.status.equals("FAILED", ignoreCase = true) }
                val warningCount = details.sourceResults.count { it.status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) }
                val latestEntries = buildCompactProgressEntries(details)
                val runIsActive = details.run.status.equals("RUNNING", ignoreCase = true)
                val activeSourceName = detectActiveSourceName(details.run, details.sourceResults, details.events)
                val subtitleParts = buildList {
                    add(translateStage(currentStageKey))
                    activeSourceName?.let { add("Источник: $it") }
                    add(formatDateTime(details.run.requestedAt ?: details.run.startedAt))
                }

                RunProgressWidget(
                    title = if (runIsActive) "Текущий запуск" else "Последний запуск",
                    subtitle = subtitleParts.joinToString(" · "),
                    statusLabel = translateRunStatus(details.run.status),
                    statusClassName = runStatusCssClass(details.run.status),
                    running = runIsActive,
                    stages = buildRunProgressStages(currentStageKey, details.run.status),
                    metrics = buildList {
                        if (!activeSourceName.isNullOrBlank()) {
                            add(RunProgressMetric("Активный источник", activeSourceName, tone = "primary"))
                        }
                        add(
                            RunProgressMetric(
                                "Длительность",
                                formatDuration(
                                    details.run.startedAt,
                                    details.run.finishedAt,
                                    running = runIsActive,
                                ),
                            ),
                        )
                        structuredSummary?.parallelism?.let {
                            add(RunProgressMetric("Параллелизм", formatNumber(it)))
                        }
                        structuredSummary?.fetchSize?.let {
                            add(RunProgressMetric("Fetch size", formatNumber(it)))
                        }
                        add(
                            RunProgressMetric(
                                "Query timeout",
                                formatEditorTimeoutSeconds(structuredSummary?.queryTimeoutSec),
                            ),
                        )
                        add(RunProgressMetric("Строк в merged", formatNumber(details.run.mergedRowCount)))
                        add(RunProgressMetric("Успешных источников", formatNumber(successCount), tone = "success"))
                        add(RunProgressMetric("Ошибок", formatNumber(failedCount), tone = if (failedCount > 0) "danger" else "default"))
                        add(RunProgressMetric("Предупреждений", formatNumber(warningCount), tone = if (warningCount > 0) "warning" else "default"))
                    },
                )

                if (latestEntries.isNotEmpty()) {
                    Div({ classes("human-log", "mt-3") }) {
                        latestEntries.forEach { entry ->
                            Div({ classes(*eventEntryCssClass(entry.severity).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                                val parts = buildList {
                                    formatDateTime(entry.timestamp).takeIf { it != "-" }?.let(::add)
                                    entry.message.takeIf { it.isNotBlank() }?.let(::add)
                                }
                                Text(parts.joinToString(" · ").ifBlank { "-" })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEditorTimeoutSeconds(value: Int?): String =
    when (value) {
        null -> "Не задан"
        else -> "${value}с"
    }

@Composable
private fun CreateModulePanel(
    state: ModuleEditorPageState,
    onModuleCodeChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
    onConfigTextChange: (String) -> Unit,
    onRestoreTemplate: () -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    val draft = state.createModuleDraft
    val actionBusy = state.actionInProgress != null
    Div({ classes("panel", "mb-4") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "justify-content-between", "gap-3", "mb-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("Новый модуль") }
                Div({ classes("text-secondary", "small") }) {
                    Text("Создай DB-модуль и сразу открой его в Compose editor.")
                }
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onRestoreTemplate() }
                }) { Text("Восстановить шаблон") }
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onCancel() }
                }) { Text("Отмена") }
                Button(attrs = {
                    classes("btn", "btn-primary")
                    attr("type", "button")
                    if (actionBusy) {
                        disabled()
                    }
                    onClick { onCreate() }
                }) { Text("Создать модуль") }
            }
        }

        Div({ classes("row", "g-4") }) {
            Div({ classes("col-12", "col-xl-4") }) {
                Div({ classes("module-metadata-card") }) {
                    Div({ classes("module-metadata-form-title") }) { Text("Параметры модуля") }
                    Div({ classes("module-metadata-form") }) {
                        MetadataTextField(
                            label = "Код модуля",
                            value = draft.moduleCode,
                            helpText = "Уникальный идентификатор модуля. Используется в URL и в каталоге.",
                            onCommit = onModuleCodeChange,
                        )
                        MetadataTextField(
                            label = "Название",
                            value = draft.title,
                            helpText = "Отображаемое имя модуля.",
                            onCommit = onTitleChange,
                        )
                        MetadataTextareaField(
                            label = "Описание",
                            value = draft.description,
                            rowsCount = 3,
                            helpText = "Кратко опиши назначение модуля.",
                            onCommit = onDescriptionChange,
                        )
                        MetadataTextField(
                            label = "Теги",
                            value = draft.tagsText,
                            helpText = "Через запятую. Пустые значения будут отброшены.",
                            onCommit = onTagsChange,
                        )
                        MetadataCheckboxField(
                            label = "Скрыть модуль в обычном каталоге UI",
                            checked = draft.hiddenFromUi,
                            helpText = "Если модуль скрыт, после создания каталог останется в режиме includeHidden.",
                            onCommit = onHiddenFromUiChange,
                        )
                    }
                }
            }
            Div({ classes("col-12", "col-xl-8") }) {
                Div({ classes("panel") }) {
                    Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-3") }) {
                        Div {
                            Div({ classes("panel-title", "mb-1") }) { Text("Стартовый application.yml") }
                            Div({ classes("text-secondary", "small") }) {
                                Text("Базовый шаблон уже валиден по структуре и подходит для дальнейшего редактирования.")
                            }
                        }
                    }
                    MonacoEditorPane(
                        instanceKey = "module-create-config",
                        language = "yaml",
                        value = draft.configText,
                        onValueChange = onConfigTextChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationAlert(session: ModuleEditorSessionResponse) {
    val issues = session.module.validationIssues
    if (issues.isEmpty() && session.module.validationStatus.equals("VALID", ignoreCase = true)) {
        return
    }
    val alertClass = when (session.module.validationStatus.uppercase()) {
        "INVALID" -> "alert alert-danger"
        else -> "alert alert-warning"
    }
    Div({ classes(*alertClass.split(" ").filter { it.isNotBlank() }.toTypedArray(), "mb-3") }) {
        Div({ classes("fw-semibold", "mb-2") }) {
            Text("Проблемы валидации модуля")
        }
        Ul({ classes("module-validation-list", "mb-0") }) {
            issues.forEach { issue ->
                Li {
                    Span({
                        classes(
                            "module-validation-severity",
                            if (issue.severity.equals("ERROR", ignoreCase = true)) {
                                "module-validation-severity-error"
                            } else {
                                "module-validation-severity-warning"
                            },
                        )
                    }) {
                        Text(if (issue.severity.equals("ERROR", ignoreCase = true)) "Ошибка" else "Предупреждение")
                    }
                    Text(issue.message)
                }
            }
        }
    }
}

@Composable
private fun CredentialsPanel(
    module: ModuleDetailsResponse,
    sectionStateKey: String?,
    uploadInProgress: Boolean,
    selectedFileName: String?,
    uploadMessage: String?,
    uploadMessageLevel: String,
    onFileSelected: (File?) -> Unit,
    onUpload: () -> Unit,
) {
    val status = module.credentialsStatus
    var expanded by remember(sectionStateKey) {
        mutableStateOf(loadEditorSectionExpanded(sectionStateKey, defaultExpanded = true))
    }
    val warningClass = when {
        !module.requiresCredentials -> "alert alert-light mb-0"
        module.credentialsReady -> "alert alert-success mb-0"
        else -> "alert alert-warning mb-0"
    }

    SectionCard(
        title = "credential.properties",
        subtitle = buildCredentialsStatusText(status),
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm", "config-section-toggle")
                attr("type", "button")
                onClick {
                    val nextValue = !expanded
                    expanded = nextValue
                    saveEditorSectionExpanded(sectionStateKey, nextValue)
                }
            }) {
                Text(if (expanded) "Свернуть" else "Развернуть")
            }
        }
    ) {
        if (expanded) {
            Div({ classes("d-flex", "flex-wrap", "align-items-center", "justify-content-between", "gap-3") }) {
                Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
                    Input(type = org.jetbrains.compose.web.attributes.InputType.File, attrs = {
                        classes("form-control")
                        attr("accept", ".properties,text/plain")
                        onChange { event ->
                            val input = event.target as? HTMLInputElement
                            onFileSelected(input?.files?.item(0))
                        }
                    })
                    Button(attrs = {
                        classes("btn", "btn-outline-dark")
                        attr("type", "button")
                        if (uploadInProgress || selectedFileName.isNullOrBlank()) {
                            disabled()
                        }
                        onClick { onUpload() }
                    }) {
                        Text(if (uploadInProgress) "Загрузка..." else "Загрузить файл")
                    }
                }
            }

            if (!selectedFileName.isNullOrBlank()) {
                Div({ classes("text-secondary", "small", "mt-2") }) {
                    Text("Выбран файл: $selectedFileName")
                }
            }

            if (!uploadMessage.isNullOrBlank()) {
                AlertBanner(uploadMessage, uploadMessageLevel)
            }

            Div({ classes(*warningClass.split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                Text(buildCredentialsWarningText(module))
            }
        }
    }
}

private fun loadEditorSectionExpanded(
    sectionStateKey: String?,
    defaultExpanded: Boolean,
): Boolean {
    if (sectionStateKey == null) {
        return defaultExpanded
    }
    return runCatching { window.localStorage.getItem(sectionStateKey) }
        .getOrNull()
        ?.let { storedValue -> storedValue == "true" }
        ?: defaultExpanded
}

private fun saveEditorSectionExpanded(
    sectionStateKey: String?,
    expanded: Boolean,
) {
    if (sectionStateKey == null) {
        return
    }
    runCatching { window.localStorage.setItem(sectionStateKey, expanded.toString()) }
}

@Composable
private fun TabNavigation(
    activeTab: ModuleEditorTab,
    onTabSelect: (ModuleEditorTab) -> Unit,
) {
    Ul({ classes("nav", "nav-tabs", "mb-3") }) {
        ModuleEditorTab.entries.forEach { tab ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (activeTab == tab) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onTabSelect(tab) }
                }) {
                    Text(tab.label)
                }
            }
        }
    }
}

@Composable
private fun SqlPreview(
    state: ModuleEditorPageState,
    selectedSqlPath: String?,
    onSelectSql: (String) -> Unit,
    onSqlChange: (String, String) -> Unit,
    onCreateSql: () -> Unit,
    onRenameSql: () -> Unit,
    onDeleteSql: () -> Unit,
) {
    val sqlFiles = buildDraftSqlFiles(state)
    val selectedSql = sqlFiles.firstOrNull { it.path == selectedSqlPath } ?: sqlFiles.firstOrNull()
    val selectedSqlContent = selectedSql?.let { sqlFile ->
        state.sqlContentsDraft[sqlFile.path] ?: sqlFile.content
    } ?: "-- SQL-ресурс не выбран"
    Div({ classes("panel") }) {
        Div({ classes("sql-catalog-layout") }) {
            Div({ classes("sql-catalog-sidebar") }) {
                Div({ classes("sql-catalog-toolbar") }) {
                    Div {
                        Div({ classes("sql-catalog-title") }) { Text("Каталог SQL-ресурсов") }
                        Div({ classes("sql-catalog-note") }) { Text("Создание, переименование и удаление SQL выполняется здесь. Изменения сохраняются основным действием редактора.") }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        Button(attrs = {
                            classes("btn", "btn-outline-primary", "btn-sm")
                            attr("type", "button")
                            onClick { onCreateSql() }
                        }) { Text("Создать SQL") }
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary", "btn-sm")
                            attr("type", "button")
                            if (selectedSql == null) {
                                disabled()
                            }
                            onClick { onRenameSql() }
                        }) { Text("Переименовать") }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            if (selectedSql == null) {
                                disabled()
                            }
                            onClick { onDeleteSql() }
                        }) { Text("Удалить") }
                    }
                }
                Div({ classes("sql-catalog-list") }) {
                    if (sqlFiles.isEmpty()) {
                        Div({ classes("text-secondary", "small") }) {
                            Text("SQL-ресурсы пока не созданы.")
                        }
                    }
                    sqlFiles.forEach { sqlFile ->
                        Button(attrs = {
                            classes("sql-catalog-item")
                            if (selectedSql?.path == sqlFile.path) {
                                classes("active")
                            }
                            attr("type", "button")
                            onClick { onSelectSql(sqlFile.path) }
                        }) {
                            Div({ classes("sql-catalog-item-title") }) { Text(sqlFile.label) }
                            Div({ classes("sql-catalog-item-meta") }) { Text(sqlFile.path) }
                        }
                    }
                }
            }

            Div({ classes("sql-catalog-workspace") }) {
                Div({ classes("sql-catalog-workspace-header") }) {
                    Div({ classes("sql-catalog-resource-title") }) {
                        Text(selectedSql?.label ?: "SQL-ресурс не выбран")
                    }
                    Div({ classes("sql-catalog-resource-meta", "text-secondary", "small") }) {
                        Text(selectedSql?.path ?: "-")
                    }
                    Div({ classes("sql-catalog-resource-usage") }) {
                        buildSqlUsageBadges(state, selectedSql?.path).forEach { badge ->
                            Span({
                                classes("sql-resource-usage-badge")
                                if (badge.muted) {
                                    classes("sql-resource-usage-badge-muted")
                                }
                            }) {
                                Text(badge.label)
                            }
                        }
                    }
                }
                MonacoEditorPane(
                    instanceKey = "module-editor-sql-${selectedSql?.path ?: "empty"}",
                    language = "sql",
                    value = selectedSqlContent,
                    readOnly = selectedSql == null,
                    onValueChange = { nextValue ->
                        val path = selectedSql?.path ?: return@MonacoEditorPane
                        onSqlChange(path, nextValue)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigPreview(
    configText: String,
    onConfigChange: (String) -> Unit,
) {
    MonacoEditorPane(
        instanceKey = "module-editor-config",
        language = "yaml",
        value = configText,
        onValueChange = onConfigChange,
    )
}

@Composable
private fun MetadataForm(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    session: ModuleEditorSessionResponse,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onHiddenFromUiChange: (Boolean) -> Unit,
) {
    val module = session.module
    val metadataDraft = state.metadataDraft
    val metadataChanged = metadataDraft.title != module.title ||
        metadataDraft.description != (module.description ?: "") ||
        metadataDraft.tags != module.tags ||
        metadataDraft.hiddenFromUi != module.hiddenFromUi
    Div({ classes("panel", "module-metadata") }) {
        if (metadataChanged) {
            AlertBanner(
                "Метаданные изменены локально. Чтобы сохранить их, используй основное действие сохранения редактора.",
                "warning",
            )
        }
        Div({ classes("module-metadata-grid") }) {
            PreviewCard("Основное") {
                MetadataReadOnlyRow("Код модуля", module.id)
                MetadataTextField(
                    label = "Название",
                    value = metadataDraft.title,
                    helpText = "Отображаемое название модуля в каталоге.",
                    onCommit = onTitleChange,
                )
                MetadataTextareaField(
                    label = "Описание",
                    value = metadataDraft.description,
                    helpText = "Короткое описание, которое видно в каталоге модулей.",
                    rowsCount = 4,
                    onCommit = onDescriptionChange,
                )
                MetadataTextField(
                    label = "Теги",
                    value = metadataDraft.tags.joinToString(", "),
                    helpText = "Список тегов через запятую.",
                ) { rawValue ->
                    onTagsChange(
                        rawValue.split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                    )
                }
                MetadataCheckboxField(
                    label = "Скрыть модуль из общего каталога UI",
                    checked = metadataDraft.hiddenFromUi,
                    helpText = if (route.storage == "database") {
                        "Полезно для DB-модулей, которые должны быть доступны только по прямому сценарию."
                    } else {
                        "Скрытый файловый модуль не показывается в основном каталоге."
                    },
                    onCommit = onHiddenFromUiChange,
                )
            }
            PreviewCard("DB lifecycle") {
                MetadataReadOnlyRow("Источник", translateSourceKind(session.sourceKind))
                MetadataReadOnlyRow("Текущая ревизия", session.currentRevisionId ?: "-")
                MetadataReadOnlyRow("Личный черновик", session.workingCopyId ?: "-")
                MetadataReadOnlyRow("Статус черновика", session.workingCopyStatus ?: "-")
                MetadataReadOnlyRow("Базовая ревизия", session.baseRevisionId ?: "-")
            }
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Div({ classes("module-metadata-card") }) {
        Div({ classes("module-metadata-form-title") }) { Text(title) }
        Div({ classes("module-metadata-form") }) {
            content()
        }
    }
}

@Composable
private fun MetadataReadOnlyRow(
    label: String,
    value: String,
) {
    Div({ classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        Div({ classes("module-metadata-value") }) { Text(value) }
    }
}

@Composable
private fun MetadataTextField(
    label: String,
    value: String,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = { classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        Div({ classes("module-metadata-value") }) {
            if (helpText.isNotBlank()) {
                Div({ classes("config-form-help", "mb-1") }) { Text(helpText) }
            }
            Input(type = org.jetbrains.compose.web.attributes.InputType.Text, attrs = {
                classes("form-control")
                value(draft)
                onInput { draft = it.value }
                onChange { if (draft != value) onCommit(draft) }
            })
        }
    }
}

@Composable
private fun MetadataTextareaField(
    label: String,
    value: String,
    rowsCount: Int,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = { classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        Div({ classes("module-metadata-value") }) {
            if (helpText.isNotBlank()) {
                Div({ classes("config-form-help", "mb-1") }) { Text(helpText) }
            }
            TextArea(value = draft, attrs = {
                classes("form-control")
                rows(rowsCount)
                onInput { draft = it.value }
                onChange { if (draft != value) onCommit(draft) }
            })
        }
    }
}

@Composable
private fun MetadataCheckboxField(
    label: String,
    checked: Boolean,
    helpText: String = "",
    onCommit: (Boolean) -> Unit,
) {
    Div({ classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text("Видимость") }
        Div({ classes("module-metadata-value") }) {
            Label(attrs = { classes("config-form-check") }) {
                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (checked) {
                        attr("checked", "checked")
                    }
                    onClick { onCommit(!checked) }
                })
                Span({ classes("form-check-label") }) { Text(label) }
            }
            if (helpText.isNotBlank()) {
                Div({ classes("config-form-help", "mt-1") }) { Text(helpText) }
            }
        }
    }
}

@Composable
private fun EditorActionButton(
    label: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", style.cssClass)
        if (!enabled) {
            disabled()
        }
        attr("type", "button")
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Text(label)
    }
}

@Composable
private fun EditorIconActionButton(
    icon: String,
    label: String,
    title: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", "module-editor-icon-btn", style.cssClass)
        attr("type", "button")
        attr("title", title)
        attr("aria-label", title)
        if (!enabled) {
            disabled()
        }
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Span({ classes("module-editor-icon-btn-icon") }) { Text(icon) }
        Span({ classes("module-editor-icon-btn-label") }) { Text(label) }
    }
}

private enum class EditorActionStyle(
    val cssClass: String,
) {
    PrimarySolid("btn-primary"),
    Success("btn-success"),
    PrimaryOutline("btn-outline-primary"),
    SecondaryOutline("btn-outline-secondary"),
    DangerOutline("btn-outline-danger"),
}

private fun buildRunsHref(
    route: ModuleEditorRouteState,
    moduleId: String?,
): String {
    val includeHiddenPart = if (route.includeHidden) "&includeHidden=true" else ""
    return "/module-runs?storage=${route.storage}&module=${moduleId.orEmpty()}$includeHiddenPart"
}

private fun buildEditorWebSocketUrl(): String {
    val protocol = if (window.location.protocol == "https:") "wss" else "ws"
    return "$protocol://${window.location.host}/ws"
}

private fun buildComposeEditorUrl(
    storage: String,
    moduleId: String?,
    includeHidden: Boolean,
): String {
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
    return buildPrimaryEditorUrl(storage, query)
}

private fun buildPrimaryEditorUrl(
    storage: String,
    query: String,
): String =
    if (storage == "database") {
        "/db-modules$query"
    } else {
        "/modules$query"
    }

private suspend fun uploadCredentialsFile(
    httpClient: ComposeHttpClient,
    file: File,
): CredentialsStatusResponse {
    val formData = FormData()
    formData.append("file", file, file.name)
    return httpClient.postFormData(
        path = "/api/credentials/upload",
        formData = formData,
        deserializer = CredentialsStatusResponse.serializer(),
    )
}

private fun validationBadgeClass(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "module-validation-badge-valid"
        "WARNING" -> "module-validation-badge-warning"
        else -> "module-validation-badge-invalid"
    }

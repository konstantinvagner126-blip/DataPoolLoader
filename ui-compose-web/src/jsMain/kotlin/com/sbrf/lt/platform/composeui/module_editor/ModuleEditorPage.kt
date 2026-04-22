package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import org.jetbrains.compose.web.dom.Div
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsApiClient
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsStore
import kotlinx.browser.window

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
    var state by remember(route.storage, route.moduleId, route.includeHidden, route.openCreateDialog) { mutableStateOf(ModuleEditorPageState()) }
    var uiState by remember(route.storage, route.moduleId) { mutableStateOf(ModuleEditorPageUiState()) }

    val session = state.session
    val selectedSql = session?.module?.sqlFiles?.firstOrNull { it.path == state.selectedSqlPath }
        ?: session?.module?.sqlFiles?.firstOrNull()
    val selectedModuleId = state.selectedModuleId
    val hasRunningRun = uiState.runPanelState.history?.runs?.any { it.status.equals("RUNNING", ignoreCase = true) } == true
    val capabilities = session?.capabilities
    val actionBusy = state.actionInProgress != null
    val callbacks = moduleEditorPageCallbacks(
        store = store,
        credentialsHttpClient = credentialsHttpClient,
        scope = rememberCoroutineScope(),
        currentRoute = { currentRoute },
        setCurrentRoute = { currentRoute = it },
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
        refreshModuleCatalog = {
            refreshModuleEditorCatalog(store, { currentRoute }, { state }, { state = it })
        },
        refreshEditorRunPanel = { moduleId ->
            refreshModuleEditorRunPanel(runsStore, { currentRoute }, { uiState }, { uiState = it }, moduleId)
        },
    )

    ModuleEditorPageEffects(
        store = store,
        runsStore = runsStore,
        currentRoute = { currentRoute },
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
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
                uiState = uiState,
                selectedModuleId = selectedModuleId,
                hasRunningRun = hasRunningRun,
                capabilities = capabilities,
                actionBusy = actionBusy,
                callbacks = callbacks,
            )
        },
        heroArt = {
            ModuleEditorHeroArt(currentRoute.storage)
        },
    )
}

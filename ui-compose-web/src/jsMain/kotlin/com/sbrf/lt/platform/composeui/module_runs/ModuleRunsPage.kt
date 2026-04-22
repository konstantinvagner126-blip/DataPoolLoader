package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.label
import com.sbrf.lt.platform.composeui.run.parseStructuredRunSummary
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeModuleRunsPage(
    route: ModuleRunsRouteState,
    api: ModuleRunsApi = remember { ModuleRunsApiClient() },
) {
    val store = remember(api) { ModuleRunsStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember(route.storage, route.moduleId) { mutableStateOf(ModuleRunsPageState()) }
    var uiState by remember(route.storage, route.moduleId) { mutableStateOf(ModuleRunsPageUiState()) }

    val session = state.session
    val history = state.history
    val details = state.selectedRunDetails
    val runtimeContext = state.runtimeContext
    val databaseFallbackActive = route.storage == "database" &&
        runtimeContext?.effectiveMode != null &&
        runtimeContext.effectiveMode != ModuleStoreMode.DATABASE
    val structuredSummary = details?.summaryJson?.let(::parseStructuredRunSummary)
    val filteredRuns = filterRuns(history?.runs.orEmpty(), state.historyFilter, state.searchQuery)
    val hasRunningRun = history?.runs?.any { it.status.equals("RUNNING", ignoreCase = true) } == true
    val callbacks = moduleRunsPageCallbacks(
        store = store,
        scope = scope,
        route = route,
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
    )

    ModuleRunsPageEffects(
        store = store,
        route = route,
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
        hasRunningRun = hasRunningRun,
    )

    PageScaffold(
        eyebrow = if (route.storage == "database") {
            "История запусков · ${ModuleStoreMode.DATABASE.label}"
        } else {
            "История запусков · ${ModuleStoreMode.FILES.label}"
        },
        title = "История и результаты",
        subtitle = if (route.storage == "database") {
            "Полная история DB-запусков, подробный ход выполнения, результаты по источникам и итоговые артефакты."
        } else {
            "Полная история файловых запусков, подробный ход выполнения, результаты по источникам и итоговые артефакты."
        },
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href(route.backHref())
                }) { Text("Вернуться к модулю") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("История и результаты") }
            }
        },
        content = {
            ModuleRunsPageContent(
                route = route,
                state = state,
                uiState = uiState,
                callbacks = callbacks,
                session = session,
                history = history,
                details = details,
                runtimeContext = runtimeContext,
                databaseFallbackActive = databaseFallbackActive,
                structuredSummary = structuredSummary,
                filteredRuns = filteredRuns,
            )
        },
        heroArt = {
            Div({ classes("flow-stage") }) {
                Div({ classes("source-node", "source-node-1") }) { Text("RUN") }
                Div({ classes("source-node", "source-node-2") }) { Text("LOG") }
                Div({ classes("source-node", "source-node-3") }) { Text("CSV") }
                Div({ classes("source-node", "source-node-4") }) { Text("JSON") }
                Div({ classes("source-node", "source-node-5") }) { Text("DB") }
                repeat(5) { index ->
                    Div({ classes("flow-line", "flow-line-${index + 1}") }) {
                        Div({ classes("flow-dot", "dot-${index + 1}") })
                    }
                }
                Div({ classes("merge-hub") }) {
                    Div({ classes("merge-title") }) { Text("RESULTS") }
                }
            }
        },
    )
}

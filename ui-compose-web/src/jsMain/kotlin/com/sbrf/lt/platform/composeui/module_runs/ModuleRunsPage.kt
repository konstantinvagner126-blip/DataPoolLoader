package com.sbrf.lt.platform.composeui.module_runs

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
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.label
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.foundation.updates.WebSocketEffect
import kotlinx.coroutines.launch
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
    var liveRefreshInProgress by remember(route.storage, route.moduleId) { mutableStateOf(false) }
    var showTechnicalDiagnostics by remember(route.storage, route.moduleId) { mutableStateOf(false) }

    LaunchedEffect(route.storage, route.moduleId) {
        state = store.startLoading(state)
        state = store.load(route, state.historyLimit)
    }

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

    PollingEffect(
        enabled = route.storage == "database" && !state.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            if (liveRefreshInProgress) {
                return@PollingEffect
            }
            liveRefreshInProgress = true
            try {
                state = store.reloadHistory(state, route)
            } finally {
                liveRefreshInProgress = false
            }
        },
    )

    PollingEffect(
        enabled = route.storage == "files" && !state.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            if (liveRefreshInProgress) {
                return@PollingEffect
            }
            liveRefreshInProgress = true
            try {
                state = store.reloadHistory(state, route)
            } finally {
                liveRefreshInProgress = false
            }
        },
    )

    WebSocketEffect(
        enabled = route.storage == "files" && !state.loading,
        url = buildRunsWebSocketUrl(),
        onMessage = {
            if (liveRefreshInProgress) {
                return@WebSocketEffect
            }
            liveRefreshInProgress = true
            try {
                state = store.reloadHistory(state, route)
            } finally {
                liveRefreshInProgress = false
            }
        },
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
                    href(buildBackHref(route))
                }) { Text("Вернуться к модулю") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("История и результаты") }
            }
        },
        content = {
            runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
                AlertBanner(
                    buildRuntimeModeFallbackMessage(fallbackContext),
                    "warning",
                )
            }

            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }

            if (state.loading && session == null) {
                LoadingStateCard(title = "История запусков", text = "Загружаю данные выбранного модуля.")
            } else {
                SectionCard(
                    title = session?.moduleTitle ?: "Модуль не выбран",
                    subtitle = session?.moduleMeta ?: "Открой этот экран из карточки выбранного модуля.",
                ) {}

                ModuleRunsOverviewStrip(
                    route = route,
                    session = session,
                    history = history,
                    details = details,
                    state = state,
                )

                if (databaseFallbackActive) {
                    EmptyStateCard(
                        title = "История запусков БД недоступна",
                        text = buildDatabaseModeUnavailableMessage(
                            runtimeContext?.fallbackReason,
                            "Режим базы данных сейчас недоступен. История DB-запусков временно недоступна.",
                        ),
                    )
                } else if (history == null || history.runs.isEmpty()) {
                    EmptyStateCard(
                        title = "История запусков",
                        text = "Для этого модуля запусков пока нет.",
                    )
                } else if (filteredRuns.isEmpty()) {
                    Div({ classes("row", "g-4") }) {
                        Div({ classes("col-12", "col-xl-4") }) {
                            RunsHistoryPanel(
                                state = state,
                                runs = filteredRuns,
                                onHistoryLimitChange = { nextLimit ->
                                    scope.launch {
                                        state = store.startLoading(state)
                                        state = store.updateHistoryLimit(state, route, nextLimit)
                                    }
                                },
                                onHistoryFilterChange = { nextFilter ->
                                    scope.launch {
                                        val nextState = store.updateHistoryFilter(state, nextFilter)
                                        val nextVisibleRuns = filterRuns(
                                            nextState.history?.runs.orEmpty(),
                                            nextState.historyFilter,
                                            nextState.searchQuery,
                                        )
                                        state = when {
                                            nextVisibleRuns.isEmpty() -> nextState.copy(
                                                selectedRunId = null,
                                                selectedRunDetails = null,
                                            )
                                            nextVisibleRuns.any { it.runId == nextState.selectedRunId } -> nextState
                                            else -> store.selectRun(nextState, route, nextVisibleRuns.first().runId)
                                        }
                                    }
                                },
                                onSearchQueryChange = { nextQuery ->
                                    scope.launch {
                                        val nextState = store.updateSearchQuery(state, nextQuery)
                                        val nextVisibleRuns = filterRuns(
                                            nextState.history?.runs.orEmpty(),
                                            nextState.historyFilter,
                                            nextState.searchQuery,
                                        )
                                        state = when {
                                            nextVisibleRuns.isEmpty() -> nextState.copy(
                                                selectedRunId = null,
                                                selectedRunDetails = null,
                                            )
                                            nextVisibleRuns.any { it.runId == nextState.selectedRunId } -> nextState
                                            else -> store.selectRun(nextState, route, nextVisibleRuns.first().runId)
                                        }
                                    }
                                },
                                onSelectRun = { },
                            )
                        }
                        Div({ classes("col-12", "col-xl-8") }) {
                            EmptyStateCard(
                                title = "Выбранный запуск",
                                text = "По выбранным фильтрам или поиску запусков нет.",
                            )
                        }
                    }
                } else {
                    Div({ classes("row", "g-4") }) {
                        Div({ classes("col-12", "col-xl-4") }) {
                            RunsHistoryPanel(
                                state = state,
                                runs = filteredRuns,
                                onHistoryLimitChange = { nextLimit ->
                                    scope.launch {
                                        state = store.startLoading(state)
                                        state = store.updateHistoryLimit(state, route, nextLimit)
                                    }
                                },
                                onHistoryFilterChange = { nextFilter ->
                                    scope.launch {
                                        val nextState = store.updateHistoryFilter(state, nextFilter)
                                        val nextVisibleRuns = filterRuns(
                                            nextState.history?.runs.orEmpty(),
                                            nextState.historyFilter,
                                            nextState.searchQuery,
                                        )
                                        state = when {
                                            nextVisibleRuns.isEmpty() -> nextState.copy(
                                                selectedRunId = null,
                                                selectedRunDetails = null,
                                            )
                                            nextVisibleRuns.any { it.runId == nextState.selectedRunId } -> nextState
                                            else -> store.selectRun(nextState, route, nextVisibleRuns.first().runId)
                                        }
                                    }
                                },
                                onSearchQueryChange = { nextQuery ->
                                    scope.launch {
                                        val nextState = store.updateSearchQuery(state, nextQuery)
                                        val nextVisibleRuns = filterRuns(
                                            nextState.history?.runs.orEmpty(),
                                            nextState.historyFilter,
                                            nextState.searchQuery,
                                        )
                                        state = when {
                                            nextVisibleRuns.isEmpty() -> nextState.copy(
                                                selectedRunId = null,
                                                selectedRunDetails = null,
                                            )
                                            nextVisibleRuns.any { it.runId == nextState.selectedRunId } -> nextState
                                            else -> store.selectRun(nextState, route, nextVisibleRuns.first().runId)
                                        }
                                    }
                                },
                                onSelectRun = { runId ->
                                    scope.launch {
                                        state = store.startLoading(state)
                                        state = store.selectRun(state, route, runId)
                                    }
                                },
                            )
                        }

                        Div({ classes("col-12", "col-xl-8") }) {
                            if (details == null) {
                                EmptyStateCard(
                                    title = "Выбранный запуск",
                                    text = "Не удалось загрузить детали запуска.",
                                )
                            } else {
                                ModuleRunDetailsPanel(
                                    details = details,
                                    history = history,
                                    structuredSummary = structuredSummary,
                                    showTechnicalDiagnostics = showTechnicalDiagnostics,
                                    onToggleTechnicalDiagnostics = {
                                        showTechnicalDiagnostics = !showTechnicalDiagnostics
                                    },
                                )
                            }
                        }
                    }
                }
            }
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

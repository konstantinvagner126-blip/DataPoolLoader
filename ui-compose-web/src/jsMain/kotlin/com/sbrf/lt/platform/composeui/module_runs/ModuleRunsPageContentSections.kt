package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import com.sbrf.lt.platform.composeui.run.StructuredRunSummary
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun ModuleRunsPageContent(
    route: ModuleRunsRouteState,
    state: ModuleRunsPageState,
    uiState: ModuleRunsPageUiState,
    callbacks: ModuleRunsPageCallbacks,
    session: ModuleRunPageSessionResponse?,
    history: ModuleRunHistoryResponse?,
    details: ModuleRunDetailsResponse?,
    runtimeContext: com.sbrf.lt.platform.composeui.model.RuntimeContext?,
    databaseFallbackActive: Boolean,
    structuredSummary: StructuredRunSummary?,
    filteredRuns: List<ModuleRunSummaryResponse>,
) {
    runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
        AlertBanner(
            buildRuntimeModeFallbackMessage(fallbackContext),
            "warning",
        )
    }

    state.errorMessage?.let { AlertBanner(it, "warning") }

    if (state.loading && session == null) {
        LoadingStateCard(title = "История запусков", text = "Загружаю данные выбранного модуля.")
        return
    }

    Div({ classes("module-runs-content-shell") }) {
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

        when {
            databaseFallbackActive -> {
                EmptyStateCard(
                    title = "История запусков БД недоступна",
                    text = buildDatabaseModeUnavailableMessage(
                        runtimeContext?.fallbackReason,
                        "Режим базы данных сейчас недоступен. История DB-запусков временно недоступна.",
                    ),
                )
            }

            history == null || history.runs.isEmpty() -> {
                EmptyStateCard(
                    title = "История запусков",
                    text = "Для этого модуля запусков пока нет.",
                )
            }

            else -> {
                Div({ classes("row", "g-4") }) {
                    Div({ classes("col-12", "col-xl-4") }) {
                        RunsHistoryPanel(
                            state = state,
                            runs = filteredRuns,
                            onHistoryLimitChange = callbacks.onHistoryLimitChange,
                            onHistoryFilterChange = callbacks.onHistoryFilterChange,
                            onSearchQueryChange = callbacks.onSearchQueryChange,
                            onSelectRun = if (filteredRuns.isEmpty()) {
                                { }
                            } else {
                                callbacks.onSelectRun
                            },
                        )
                    }

                    Div({ classes("col-12", "col-xl-8") }) {
                        when {
                            filteredRuns.isEmpty() -> {
                                EmptyStateCard(
                                    title = "Выбранный запуск",
                                    text = "По выбранным фильтрам или поиску запусков нет.",
                                )
                            }

                            details == null -> {
                                EmptyStateCard(
                                    title = "Выбранный запуск",
                                    text = "Не удалось загрузить детали запуска.",
                                )
                            }

                            else -> {
                                ModuleRunDetailsPanel(
                                    details = details,
                                    history = history,
                                    structuredSummary = structuredSummary,
                                    showTechnicalDiagnostics = uiState.showTechnicalDiagnostics,
                                    onToggleTechnicalDiagnostics = callbacks.onToggleTechnicalDiagnostics,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

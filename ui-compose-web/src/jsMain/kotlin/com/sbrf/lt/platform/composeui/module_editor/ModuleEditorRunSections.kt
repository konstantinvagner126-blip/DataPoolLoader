package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressMetric
import com.sbrf.lt.platform.composeui.foundation.component.RunProgressWidget
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.buildRunProgressStages
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.buildCompactProgressEntries
import com.sbrf.lt.platform.composeui.module_runs.detectActiveSourceName
import com.sbrf.lt.platform.composeui.module_runs.detectRunStageKey
import com.sbrf.lt.platform.composeui.module_runs.eventEntryCssClass
import com.sbrf.lt.platform.composeui.module_runs.parseStructuredRunSummary
import com.sbrf.lt.platform.composeui.module_runs.runStatusCssClass
import com.sbrf.lt.platform.composeui.module_runs.translateRunStatus
import com.sbrf.lt.platform.composeui.module_runs.translateStage
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
internal fun EditorRunOverviewPanel(
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
                            Div({ classesFromString(eventEntryCssClass(entry.severity)) }) {
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

@Composable
internal fun ValidationAlert(session: ModuleEditorSessionResponse) {
    val issues = session.module.validationIssues
    if (issues.isEmpty() && session.module.validationStatus.equals("VALID", ignoreCase = true)) {
        return
    }
    val alertClass = when (session.module.validationStatus.uppercase()) {
        "INVALID" -> "alert alert-danger"
        else -> "alert alert-warning"
    }
    Div({
        classes("mb-3")
        classesFromString(alertClass)
    }) {
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
internal fun CredentialsPanel(
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
        },
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

            Div({ classesFromString(warningClass) }) {
                Text(buildCredentialsWarningText(module))
            }
        }
    }
}

@Composable
internal fun TabNavigation(
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

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
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.module_editor.buildCredentialsStatusText
import com.sbrf.lt.platform.composeui.module_editor.buildCredentialsWarningText
import com.sbrf.lt.platform.composeui.module_editor.buildDraftStatusText
import com.sbrf.lt.platform.composeui.module_editor.translateSourceKind
import com.sbrf.lt.platform.composeui.module_editor.translateValidationStatus
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.buildCompactProgressEntries
import com.sbrf.lt.platform.composeui.module_runs.detectActiveSourceName
import com.sbrf.lt.platform.composeui.module_runs.detectRunStageKey
import com.sbrf.lt.platform.composeui.module_runs.eventEntryCssClass
import com.sbrf.lt.platform.composeui.module_runs.parseStructuredRunSummary
import com.sbrf.lt.platform.composeui.module_runs.runStatusCssClass
import com.sbrf.lt.platform.composeui.module_runs.translateRunStatus
import com.sbrf.lt.platform.composeui.module_runs.translateStage
import kotlinx.browser.window
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
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
internal fun ModuleRunningBadge() {
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
internal fun EditorErrorMessageBox(
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
internal fun DatabaseModeAlert(
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
internal fun EditorShellHeader(
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

@Composable
internal fun CreateModulePanel(
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
                    com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
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
internal fun ValidationAlert(session: ModuleEditorSessionResponse) {
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

            Div({ classes(*warningClass.split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
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

@Composable
internal fun SqlPreview(
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
                com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
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
internal fun ConfigPreview(
    configText: String,
    onConfigChange: (String) -> Unit,
) {
    com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane(
        instanceKey = "module-editor-config",
        language = "yaml",
        value = configText,
        onValueChange = onConfigChange,
    )
}

@Composable
internal fun MetadataForm(
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
internal fun PreviewCard(
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
internal fun MetadataReadOnlyRow(
    label: String,
    value: String,
) {
    Div({ classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        Div({ classes("module-metadata-value") }) { Text(value) }
    }
}

@Composable
internal fun MetadataTextField(
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
internal fun MetadataTextareaField(
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
internal fun MetadataCheckboxField(
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
internal fun EditorActionButton(
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
internal fun EditorIconActionButton(
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

internal enum class EditorActionStyle(
    val cssClass: String,
) {
    PrimarySolid("btn-primary"),
    Success("btn-success"),
    PrimaryOutline("btn-outline-primary"),
    SecondaryOutline("btn-outline-secondary"),
    DangerOutline("btn-outline-danger"),
}

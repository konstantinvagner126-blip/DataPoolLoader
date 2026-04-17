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
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
fun ComposeModuleEditorPage(
    route: ModuleEditorRouteState,
    api: ModuleEditorApi = remember { ModuleEditorApiClient() },
) {
    val store = remember(api) { ModuleEditorStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember(route.storage, route.moduleId, route.includeHidden) { mutableStateOf(ModuleEditorPageState()) }

    LaunchedEffect(route.storage, route.moduleId, route.includeHidden) {
        state = store.startLoading(state)
        state = store.load(route)
    }

    val session = state.session
    val selectedSql = session?.module?.sqlFiles?.firstOrNull { it.path == state.selectedSqlPath }
        ?: session?.module?.sqlFiles?.firstOrNull()

    PageScaffold(
        eyebrow = if (route.storage == "database") "Режим базы данных" else "Файловый режим",
        title = if (route.storage == "database") "Модули из базы данных" else "Управление пулами данных НТ",
        subtitle = if (route.storage == "database") {
            "Просмотр и поэтапный перевод DB-редактора на Compose без замены production UI."
        } else {
            "Просмотр и поэтапный перевод файлового редактора на Compose без замены production UI."
        },
        content = {
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }

            Div({ classes("row", "g-4") }) {
                Div({ classes("col-12", "col-xl-3") }) {
                    Div({ classes("panel", "h-100") }) {
                        Div({ classes("mb-3") }) {
                            A(attrs = {
                                classes("btn", "btn-outline-dark", "w-100")
                                href("/compose-spike")
                            }) {
                                Text("На Compose-главную")
                            }
                        }
                        Div({ classes("panel-title") }) {
                            Text(if (route.storage == "database") "Модули из базы данных" else "Модули")
                        }
                        Div({
                            classes("module-catalog-status", "mt-3", "mb-3", "text-secondary", "small")
                        }) {
                            Text(buildCatalogStatus(state, route.storage))
                        }
                        if (state.loading && state.modules.isEmpty()) {
                            P({ classes("text-secondary", "small", "mb-0") }) {
                                Text("Каталог модулей загружается.")
                            }
                        } else {
                            Div({ classes("list-group", "module-list") }) {
                                state.modules.forEach { module ->
                                    Button(attrs = {
                                        classes(
                                            "list-group-item",
                                            "list-group-item-action",
                                            if (module.id == state.selectedModuleId) "active" else "",
                                        )
                                        attr("type", "button")
                                        onClick {
                                            scope.launch {
                                                state = store.startLoading(state)
                                                state = store.selectModule(state, route, module.id)
                                            }
                                        }
                                    }) {
                                        Div({ classes("module-list-head") }) {
                                            Div {
                                                Div({ classes("module-list-title") }) {
                                                    Text(module.title.ifBlank { module.id })
                                                }
                                                if (!module.description.isNullOrBlank()) {
                                                    Div({ classes("module-list-description") }) {
                                                        Text(module.description)
                                                    }
                                                }
                                            }
                                            Span({
                                                classes("module-validation-badge", validationBadgeClass(module.validationStatus))
                                            }) {
                                                Text(translateValidationStatus(module.validationStatus))
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
                        EditorShellHeader(
                            route = route,
                            state = state,
                            onTabSelect = { tab -> state = store.selectTab(state, tab) },
                        )

                        ValidationAlert(session)

                        TabNavigation(
                            activeTab = state.activeTab,
                            onTabSelect = { tab -> state = store.selectTab(state, tab) },
                        )

                        when (state.activeTab) {
                            ModuleEditorTab.SETTINGS -> SettingsPreview(session)
                            ModuleEditorTab.SQL -> SqlPreview(
                                session = session,
                                selectedSqlPath = selectedSql?.path,
                                onSelectSql = { path -> state = store.selectSqlResource(state, path) },
                            )
                            ModuleEditorTab.CONFIG -> ConfigPreview(session)
                            ModuleEditorTab.META -> MetadataPreview(session)
                        }
                    }
                }
            }
        },
        heroArt = {
            if (route.storage == "database") {
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
private fun EditorShellHeader(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    onTabSelect: (ModuleEditorTab) -> Unit,
) {
    val session = requireNotNull(state.session)
    val module = session.module
    val capabilities = session.capabilities

    Div({ classes("panel", "mb-4") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "justify-content-between", "gap-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("Редактор модуля") }
                Div({ classes("text-secondary", "small") }) { Text(module.title.ifBlank { module.id }) }
                if (!module.description.isNullOrBlank()) {
                    Div({ classes("text-secondary", "small", "mt-1") }) { Text(module.description) }
                }
                Div({ classes("module-draft-status", "small", "mt-1", "text-secondary") }) {
                    Span({
                        classes(
                            "module-draft-dot",
                            if (route.storage == "database" && !session.workingCopyId.isNullOrBlank()) {
                                "module-draft-dot-warning"
                            } else {
                                "module-draft-dot-saved"
                            },
                        )
                        attr("aria-hidden", "true")
                    })
                    Span {
                        Text(buildDraftStatusText(route, session))
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
                            EditorActionButton("Запустить", capabilities.run, true)
                            A(attrs = {
                                classes(
                                    "btn",
                                    "btn-outline-secondary",
                                    if (state.selectedModuleId == null) "disabled" else "",
                                )
                                if (state.selectedModuleId != null) {
                                    href(buildRunsHref(route, state.selectedModuleId))
                                } else {
                                    attr("aria-disabled", "true")
                                }
                            }) { Text("История и результаты") }
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-draft") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Личный черновик") }
                            EditorActionButton("Сохранить черновик", capabilities.saveWorkingCopy)
                            EditorActionButton("Опубликовать", capabilities.publish, true)
                            EditorActionButton("Сбросить черновик", capabilities.discardWorkingCopy)
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Редактор") }
                            EditorActionButton("Отменить изменения", false)
                        }
                    }
                    Div({ classes("module-editor-toolbar-row") }) {
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-admin") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Администрирование") }
                            EditorActionButton("Новый модуль", capabilities.createModule)
                            EditorActionButton("Удалить модуль", capabilities.deleteModule)
                            A(attrs = {
                                classes("btn", "btn-outline-secondary")
                                href("/db-sync")
                            }) { Text("Импорт из файлов") }
                        }
                    }
                }
            } else {
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    EditorActionButton("Отменить изменения", false)
                    EditorActionButton("Сохранить", capabilities.save)
                    A(attrs = {
                        classes(
                            "btn",
                            "btn-outline-secondary",
                            if (state.selectedModuleId == null) "disabled" else "",
                        )
                        if (state.selectedModuleId != null) {
                            href(buildRunsHref(route, state.selectedModuleId))
                        } else {
                            attr("aria-disabled", "true")
                        }
                    }) { Text("История и результаты") }
                    EditorActionButton("Запустить", capabilities.run, true)
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
    Div({ classes(alertClass, "mb-3") }) {
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
private fun TabNavigation(
    activeTab: ModuleEditorTab,
    onTabSelect: (ModuleEditorTab) -> Unit,
) {
    Ul({ classes("nav", "nav-tabs", "mb-3") }) {
        ModuleEditorTab.entries.forEach { tab ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link", if (activeTab == tab) "active" else "")
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
private fun SettingsPreview(session: ModuleEditorSessionResponse) {
    val module = session.module
    Div({ classes("panel") }) {
        Div({ classes("config-form-note", "text-secondary", "small", "mb-3") }) {
            Text("Это первый Compose-shell для редактора. Здесь пока read-only preview без form-state и Monaco bridge.")
        }
        Div({ classes("module-metadata-grid") }) {
            PreviewCard("Credentials") {
                MetadataRow("Режим", module.credentialsStatus.mode)
                MetadataRow("Источник", module.credentialsStatus.displayName)
                MetadataRow("Файл доступен", if (module.credentialsStatus.fileAvailable) "Да" else "Нет")
                MetadataRow("Загружен через UI", if (module.credentialsStatus.uploaded) "Да" else "Нет")
                MetadataRow("Требуются credentials", if (module.requiresCredentials) "Да" else "Нет")
                MetadataRow("Готово к запуску", if (module.credentialsReady) "Да" else "Нет")
            }
            PreviewCard("Плейсхолдеры") {
                MetadataRow("Требуемые ключи", module.requiredCredentialKeys.joinToString(", ").ifBlank { "-" })
                MetadataRow("Отсутствующие ключи", module.missingCredentialKeys.joinToString(", ").ifBlank { "-" })
                MetadataRow("Путь config", module.configPath)
            }
        }
    }
}

@Composable
private fun SqlPreview(
    session: ModuleEditorSessionResponse,
    selectedSqlPath: String?,
    onSelectSql: (String) -> Unit,
) {
    val selectedSql = session.module.sqlFiles.firstOrNull { it.path == selectedSqlPath } ?: session.module.sqlFiles.firstOrNull()
    Div({ classes("panel") }) {
        Div({ classes("sql-catalog-layout") }) {
            Div({ classes("sql-catalog-sidebar") }) {
                Div({ classes("sql-catalog-toolbar") }) {
                    Div {
                        Div({ classes("sql-catalog-title") }) { Text("Каталог SQL-ресурсов") }
                        Div({ classes("sql-catalog-note") }) { Text("На этом шаге Compose показывает read-only preview SQL-ресурсов.") }
                    }
                }
                Div({ classes("sql-catalog-list") }) {
                    session.module.sqlFiles.forEach { sqlFile ->
                        Button(attrs = {
                            classes(
                                "sql-catalog-item",
                                if (selectedSql?.path == sqlFile.path) "active" else "",
                            )
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
                        Span({ classes("sql-resource-usage-badge", "sql-resource-usage-badge-muted") }) {
                            Text("Monaco bridge будет подключен на следующем этапе")
                        }
                    }
                }
                Pre({ classes("editor-frame", "p-3", "bg-light", "border", "rounded-3", "mb-0") }) {
                    Text(selectedSql?.content ?: "-- SQL-ресурс не выбран")
                }
            }
        }
    }
}

@Composable
private fun ConfigPreview(session: ModuleEditorSessionResponse) {
    Div({ classes("panel") }) {
        Pre({ classes("editor-frame", "p-3", "bg-light", "border", "rounded-3", "mb-0") }) {
            Text(session.module.configText)
        }
    }
}

@Composable
private fun MetadataPreview(session: ModuleEditorSessionResponse) {
    val module = session.module
    Div({ classes("panel", "module-metadata") }) {
        Div({ classes("module-metadata-grid") }) {
            PreviewCard("Основное") {
                MetadataRow("Код модуля", module.id)
                MetadataRow("Название", module.title)
                MetadataRow("Описание", module.description ?: "-")
                MetadataRow("Теги", module.tags.joinToString(", ").ifBlank { "-" })
                MetadataRow("Скрыт из UI", if (module.hiddenFromUi) "Да" else "Нет")
            }
            PreviewCard("DB lifecycle") {
                MetadataRow("Источник", translateSourceKind(session.sourceKind))
                MetadataRow("Current revision", session.currentRevisionId ?: "-")
                MetadataRow("Working copy", session.workingCopyId ?: "-")
                MetadataRow("Статус черновика", session.workingCopyStatus ?: "-")
                MetadataRow("Base revision", session.baseRevisionId ?: "-")
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
private fun MetadataRow(
    label: String,
    value: String,
) {
    Div({ classes("module-metadata-row") }) {
        Div({ classes("module-metadata-label") }) { Text(label) }
        Div({ classes("module-metadata-value") }) { Text(value) }
    }
}

@Composable
private fun EditorActionButton(
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
) {
    Button(attrs = {
        classes("btn", if (primary) "btn-success" else "btn-outline-secondary")
        if (!enabled) {
            disabled()
        }
        attr("type", "button")
    }) {
        Text(label)
    }
}

private fun buildCatalogStatus(
    state: ModuleEditorPageState,
    storage: String,
): String {
    val totalModules = state.modules.size
    return if (storage == "database") {
        "Compose-shell каталога БД. Модулей: $totalModules."
    } else {
        "Compose-shell файлового каталога. Модулей: $totalModules."
    }
}

private fun buildRunsHref(
    route: ModuleEditorRouteState,
    moduleId: String?,
): String {
    val includeHiddenPart = if (route.includeHidden) "&includeHidden=true" else ""
    return "/compose-runs?storage=${route.storage}&module=${moduleId.orEmpty()}$includeHiddenPart"
}

private fun buildDraftStatusText(
    route: ModuleEditorRouteState,
    session: ModuleEditorSessionResponse,
): String =
    if (route.storage == "database") {
        when {
            !session.workingCopyId.isNullOrBlank() -> "Есть личный черновик."
            else -> "Изменений в личном черновике нет."
        }
    } else {
        "Изменений нет."
    }

private fun translateSourceKind(sourceKind: String?): String =
    when (sourceKind?.uppercase()) {
        "WORKING_COPY" -> "Личный черновик"
        "CURRENT_REVISION" -> "Текущая ревизия"
        null -> "-"
        else -> sourceKind
    }

private fun validationBadgeClass(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "module-validation-badge-valid"
        "WARNING" -> "module-validation-badge-warning"
        else -> "module-validation-badge-invalid"
    }

private fun translateValidationStatus(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "Валидно"
        "WARNING" -> "Есть предупреждения"
        else -> "Есть ошибки"
    }

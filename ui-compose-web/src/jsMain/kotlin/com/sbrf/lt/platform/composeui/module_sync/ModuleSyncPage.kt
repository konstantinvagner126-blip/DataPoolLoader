package com.sbrf.lt.platform.composeui.module_sync

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
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
fun ComposeModuleSyncPage(
    api: ModuleSyncApi = remember { ModuleSyncApiClient() },
) {
    val store = remember(api) { ModuleSyncStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ModuleSyncPageState()) }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = store.load()
    }

    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    val databaseModeActive = runtimeContext?.effectiveMode == ModuleStoreMode.DATABASE
    val hasActiveSync = syncState?.maintenanceMode == true || syncState?.activeSingleSyncs?.isNotEmpty() == true

    PollingEffect(
        enabled = !state.loading && (databaseModeActive || hasActiveSync),
        intervalMs = 5000,
        onTick = {
            state = store.refresh(state)
        },
    )

    PageScaffold(
        eyebrow = "Режим базы данных",
        title = "Импорт модулей из файлов",
        subtitle = "Синхронизация файловых модулей из каталога apps в базу данных. Создание модулей на основе application.yml и SQL-ресурсов.",
        content = {
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }

            RuntimeAlert(state)

            Div({ classes("panel") }) {
                Div({ classes("panel-title") }) { Text("Импорт модулей") }
                P({ classes("text-secondary", "small", "mt-2") }) {
                    Text(
                        "Импорт просматривает каталог apps, находит модули с application.yml и создает соответствующие записи в базе данных. " +
                            "Модули с тем же составом конфигурации и SQL-ресурсов пропускаются.",
                    )
                }

                SyncActionsPanel(
                    state = state,
                    onModuleCodeChange = { state = store.updateSyncOneModuleCode(state, it) },
                    onToggleSyncOneInput = { state = store.toggleSyncOneInput(state) },
                    onSyncAll = {
                        if (!window.confirm("Синхронизировать все файловые модули в базу данных?")) {
                            return@SyncActionsPanel
                        }
                        scope.launch {
                            state = store.beginAction(state, "sync-all")
                            state = store.syncAll(state)
                        }
                    },
                    onSyncOne = {
                        scope.launch {
                            state = store.beginAction(state, "sync-one")
                            state = store.syncOne(state)
                        }
                    },
                )

                Div({ classes("row", "g-4", "mt-1") }) {
                    Div({ classes("col-12", "col-lg-4") }) {
                        SyncRunsHistoryPanel(
                            state = state,
                            onLimitChange = { limit ->
                                scope.launch {
                                    state = store.startLoading(store.updateHistoryLimit(state, limit))
                                    state = store.load(
                                        historyLimit = limit,
                                        preferredRunId = state.selectedRunId,
                                        syncOneModuleCode = state.syncOneModuleCode,
                                    )
                                }
                            },
                            onSelectRun = { syncRunId ->
                                scope.launch {
                                    state = store.selectRun(state, syncRunId)
                                }
                            },
                        )
                    }
                    Div({ classes("col-12", "col-lg-8") }) {
                        SyncRunDetailsPanel(state)
                    }
                }
            }
        },
    )
}

@Composable
private fun RuntimeAlert(state: ModuleSyncPageState) {
    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    when {
        runtimeContext == null -> return
        runtimeContext.effectiveMode != ModuleStoreMode.DATABASE -> {
            AlertBanner(
                runtimeContext.fallbackReason ?: "Для импорта нужен активный режим «База данных».",
                "warning",
            )
        }
        syncState?.maintenanceMode == true -> {
            AlertBanner(buildMaintenanceMessage(syncState), "warning")
        }
        syncState?.activeSingleSyncs?.isNotEmpty() == true -> {
            AlertBanner(
                syncState.activeSingleSyncs.joinToString(" ") { describeActiveSingleSync(it) },
                "info",
            )
        }
    }
}

@Composable
private fun SyncActionsPanel(
    state: ModuleSyncPageState,
    onModuleCodeChange: (String) -> Unit,
    onToggleSyncOneInput: () -> Unit,
    onSyncAll: () -> Unit,
    onSyncOne: () -> Unit,
) {
    val runtimeContext = state.runtimeContext
    val syncState = state.syncState
    val databaseModeActive = runtimeContext?.effectiveMode == ModuleStoreMode.DATABASE
    val maintenanceMode = syncState?.maintenanceMode == true
    val activeSingleSync = activeSingleSyncFor(state.syncOneModuleCode, syncState)
    val canSyncAll = databaseModeActive && !maintenanceMode && state.actionInProgress == null && activeSingleSync == null
    val canSyncOne = databaseModeActive && !maintenanceMode && state.actionInProgress == null

    Div({ classes("d-flex", "flex-wrap", "gap-2", "mb-4") }) {
        Button(attrs = {
            classes("btn", "btn-primary")
            attr("type", "button")
            if (!canSyncAll) {
                disabled()
            }
            onClick { onSyncAll() }
        }) {
            Text(if (state.actionInProgress == "sync-all") "Синхронизация..." else "Синхронизировать все модули")
        }
        Button(attrs = {
            classes("btn", "btn-outline-primary")
            attr("type", "button")
            if (!canSyncOne) {
                disabled()
            }
            onClick { onToggleSyncOneInput() }
        }) {
            Text(if (state.syncOneInputVisible) "Скрыть форму одного модуля" else "Синхронизировать один модуль")
        }
    }

    if (state.syncOneInputVisible) {
        Div({ classes("mb-3") }) {
            Label(attrs = { classes("form-label", "small", "text-secondary") }) {
                Text("Код модуля для синхронизации:")
            }
            Div({ classes("input-group") }) {
                Input(type = org.jetbrains.compose.web.attributes.InputType.Text, attrs = {
                    classes("form-control")
                    value(state.syncOneModuleCode)
                    attr("placeholder", "например: local-manual-test")
                    onInput { onModuleCodeChange(it.value) }
                })
                Button(attrs = {
                    classes("btn", "btn-primary")
                    attr("type", "button")
                    if (!canSyncOne || activeSingleSync != null) {
                        disabled()
                    }
                    onClick { onSyncOne() }
                }) {
                    Text(if (state.actionInProgress == "sync-one") "Синхронизация..." else "Синхронизировать")
                }
            }
            Div({ classes("small", "text-secondary", "mt-2") }) {
                Text("Используй точечный импорт, когда нужно пересобрать только один DB-модуль без массовой синхронизации всего каталога.")
            }
            if (activeSingleSync != null) {
                Div({ classes("small", "text-secondary", "mt-2") }) {
                    Text(describeActiveSingleSync(activeSingleSync))
                }
            }
        }
    }
}

@Composable
private fun SyncRunsHistoryPanel(
    state: ModuleSyncPageState,
    onLimitChange: (Int) -> Unit,
    onSelectRun: (String) -> Unit,
) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("d-flex", "flex-wrap", "justify-content-between", "align-items-center", "gap-2") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("История импортов") }
            Div({ classes("d-flex", "align-items-center", "gap-2") }) {
                Label(attrs = { classes("small", "text-secondary", "mb-0") }) { Text("Показывать") }
                Select(attrs = {
                    classes("form-select", "form-select-sm")
                    attr("style", "width: auto;")
                    if (state.actionInProgress != null) {
                        disabled()
                    }
                    onChange {
                        onLimitChange(it.value?.toIntOrNull() ?: state.historyLimit)
                    }
                }) {
                    listOf(10, 20, 50).forEach { limit ->
                        Option(value = limit.toString(), attrs = {
                            if (state.historyLimit == limit) {
                                selected()
                            }
                        }) {
                            Text(limit.toString())
                        }
                    }
                }
            }
        }

        Div({ classes("mt-3") }) {
            when {
                state.loading && state.runs.isEmpty() -> {
                    LoadingStateCard("История импортов", "Загружаю последние синхронизации.")
                }
                state.runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE -> {
                    EmptyStateCard("История импортов", "История станет доступна после переключения в режим «База данных».")
                }
                state.runs.isEmpty() -> {
                    EmptyStateCard("История импортов", "Импорты пока не запускались.")
                }
                else -> {
                    state.runs.forEach { run ->
                        Button(attrs = {
                            classes("run-history-item")
                            if (run.syncRunId == state.selectedRunId) {
                                classes("run-history-item-active")
                            }
                            attr("type", "button")
                            onClick { onSelectRun(run.syncRunId) }
                        }) {
                            Div({ classes("run-history-head") }) {
                                Span({ classes("run-history-title") }) { Text(syncRunTitle(run)) }
                                Span({ classes("badge", *syncStatusBadgeClass(run.status).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
                                    Text(translateSyncStatus(run.status))
                                }
                            }
                            Div({ classes("run-history-meta") }) { Text(formatInstant(run.startedAt)) }
                            Div({ classes("run-history-meta") }) {
                                Text(
                                    "Обработано: ${run.totalProcessed} · создано: ${run.totalCreated} · " +
                                        "пропущено: ${run.totalSkipped} · ошибок: ${run.totalFailed}",
                                )
                            }
                            Div({ classes("run-history-meta") }) {
                                Text(syncRunMeta(run))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncRunDetailsPanel(state: ModuleSyncPageState) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("panel-title") }) { Text("Детали импорта") }

        Div({ classes("mt-3") }) {
            val details = state.selectedRunDetails
            if (details == null) {
                EmptyStateCard("Детали импорта", "Выбери запуск из истории слева, чтобы посмотреть детали.")
                return@Div
            }

            val run = details.run
            Div({ classes("d-flex", "flex-wrap", "gap-3", "mb-3") }) {
                Span({ classes("badge", *syncStatusBadgeClass(run.status).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) { Text(translateSyncStatus(run.status)) }
                SummaryMetric("Область", translateSyncScope(run.scope))
                SummaryMetric("Обработано", run.totalProcessed.toString())
                SummaryMetric("Создано", run.totalCreated.toString())
                SummaryMetric("Обновлено", run.totalUpdated.toString())
                SummaryMetric("Пропущено", run.totalSkipped.toString())
                SummaryMetric("С ошибкой", run.totalFailed.toString())
            }

            Div({ classes("run-summary-list") }) {
                SyncSummaryRow("Запуск", syncRunTitle(run))
                SyncSummaryRow("Инициатор", run.startedByActorDisplayName ?: run.startedByActorId ?: "-")
                SyncSummaryRow("Старт", formatInstant(run.startedAt))
                SyncSummaryRow("Завершение", run.finishedAt?.let(::formatInstant) ?: "еще выполняется")
                SyncSummaryRow("Идентификатор", run.syncRunId)
            }

            if (details.items.isEmpty()) {
                Div({ classes("text-secondary", "small", "mt-3") }) {
                    Text("Детали по модулям появятся после завершения импорта.")
                }
                return@Div
            }

            Div({ classes("mt-3") }) {
                details.items.forEach { item ->
                    Div({ classes("card", "mb-3") }) {
                        Div({ classes("card-body", "py-3", "px-3") }) {
                            Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
                                Code({ classes("fw-semibold") }) { Text(item.moduleCode) }
                                Span({ classes("badge", *syncActionBadgeClass(item.action).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) { Text(translateSyncAction(item.action)) }
                                Span({ classes("badge", *syncStatusBadgeClass(item.status).split(" ").filter { it.isNotBlank() }.toTypedArray()) }) { Text(translateSyncStatus(item.status)) }
                                if (!item.resultRevisionId.isNullOrBlank()) {
                                    Span({ classes("small", "text-secondary") }) { Text("Ревизия: ${item.resultRevisionId}") }
                                }
                            }
                            val errorMessage = item.errorMessage
                            if (!errorMessage.isNullOrBlank()) {
                                Div({ classes("small", "text-danger", "mt-2") }) { Text(errorMessage) }
                            }
                            val details = item.details
                            val reason = details?.get("reason")?.toString()?.trim('"')
                            val message = details?.get("message")?.toString()?.trim('"')
                            if (!reason.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary", "mt-2") }) { Text("Причина: $reason") }
                            }
                            if (!message.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary") }) { Text(message) }
                            }
                            if (details != null && details.isNotEmpty()) {
                                Pre({ classes("bg-body-tertiary", "rounded", "p-2", "mt-2", "small", "mb-0") }) {
                                    Text(details.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
) {
    Span {
        Text("$label: ")
        Span({ classes("fw-semibold") }) { Text(value) }
    }
}

@Composable
private fun SyncSummaryRow(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-item") }) {
        Div({ classes("run-summary-label") }) { Text(label) }
        Div({ classes("run-summary-value") }) { Text(value) }
    }
}

private fun buildMaintenanceMessage(syncState: ModuleSyncStateResponse): String {
    val active = syncState.activeFullSync
    val actor = active?.startedByActorDisplayName ?: active?.startedByActorId
    val startedAt = active?.startedAt?.let(::formatInstant)
    return listOf(
        syncState.message,
        actor?.let { "Инициатор: $it." },
        startedAt?.let { "Запуск: $it." },
    ).filterNotNull().joinToString(" ")
}

private fun activeSingleSyncFor(
    moduleCode: String,
    syncState: ModuleSyncStateResponse?,
): ActiveModuleSyncRunResponse? {
    val normalized = moduleCode.trim()
    if (normalized.isBlank() || syncState == null) {
        return null
    }
    return syncState.activeSingleSyncs.firstOrNull { it.moduleCode == normalized }
}

private fun describeActiveSingleSync(sync: ActiveModuleSyncRunResponse): String {
    val actor = sync.startedByActorDisplayName ?: sync.startedByActorId
    val startedAt = formatInstant(sync.startedAt)
    return buildString {
        append("Идет импорт модуля '${sync.moduleCode ?: "-"}'.")
        if (!actor.isNullOrBlank()) {
            append(" Инициатор: $actor.")
        }
        append(" Запуск: $startedAt.")
    }
}

private fun syncRunTitle(run: ModuleSyncRunSummaryResponse): String =
    if (run.scope.equals("ONE", ignoreCase = true) && !run.moduleCode.isNullOrBlank()) {
        "Модуль ${run.moduleCode}"
    } else {
        translateSyncScope(run.scope)
    }

private fun syncRunMeta(run: ModuleSyncRunSummaryResponse): String {
    val actor = run.startedByActorDisplayName ?: run.startedByActorId
    val finishedAt = run.finishedAt?.let(::formatInstant) ?: "Запуск еще выполняется"
    return listOfNotNull(
        actor?.let { "Инициатор: $it" },
        "Завершение: $finishedAt",
    ).joinToString(" · ")
}

private fun translateSyncStatus(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "Успешно"
        "FAILED" -> "Ошибка"
        "RUNNING" -> "Выполняется"
        "PARTIAL_SUCCESS" -> "Частично успешно"
        else -> status
    }

private fun translateSyncScope(scope: String): String =
    when (scope.uppercase()) {
        "ALL" -> "Все модули"
        "ONE" -> "Один модуль"
        else -> scope
    }

private fun translateSyncAction(action: String): String =
    when (action.uppercase()) {
        "CREATED" -> "Создан"
        "UPDATED" -> "Обновлён"
        "SKIPPED" -> "Пропущен"
        "SKIPPED_NO_CHANGES" -> "Пропущен без изменений"
        "SKIPPED_CODE_CONFLICT" -> "Пропущен из-за конфликта кода"
        "FAILED" -> "Ошибка"
        else -> action
    }

private fun syncStatusBadgeClass(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "bg-success"
        "FAILED" -> "bg-danger"
        "RUNNING" -> "bg-primary"
        "PARTIAL_SUCCESS" -> "bg-warning text-dark"
        else -> "bg-secondary"
    }

private fun syncActionBadgeClass(action: String): String =
    when (action.uppercase()) {
        "CREATED" -> "bg-success"
        "UPDATED" -> "bg-primary"
        "SKIPPED", "SKIPPED_NO_CHANGES", "SKIPPED_CODE_CONFLICT" -> "bg-secondary"
        "FAILED" -> "bg-danger"
        else -> "bg-secondary"
    }

private fun formatInstant(value: String): String {
    val date = js("new Date(value)")
    val millis = date.getTime() as Double
    if (millis.isNaN()) {
        return value
    }
    return date.toLocaleString("ru-RU") as String
}

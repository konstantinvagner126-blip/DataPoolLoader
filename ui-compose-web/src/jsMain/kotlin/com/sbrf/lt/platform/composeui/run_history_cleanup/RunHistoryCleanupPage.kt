package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeRunHistoryCleanupPage(
    api: RunHistoryCleanupApi = remember { RunHistoryCleanupApiClient() },
) {
    val store = remember(api) { RunHistoryCleanupStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(RunHistoryCleanupPageState()) }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = store.load()
    }

    val runtimeContext = state.runtimeContext
    val storageMode = runtimeContext?.requestedMode

    PageScaffold(
        eyebrow = "Нагрузочное тестирование",
        title = "Обслуживание запусков",
        subtitle = buildSubtitle(storageMode),
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
                }) { Text("Очистка истории") }
            }
        },
        content = {
            runtimeContext?.takeIf { it.requestedMode != it.effectiveMode }?.let { fallbackContext ->
                AlertBanner(
                    buildFallbackWarning(fallbackContext),
                    "warning",
                )
            }
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }

            CleanupSection(
                state = state,
                onToggleDisableSafeguard = { disableSafeguard ->
                    scope.launch {
                        state = store.updateCleanupSafeguard(state, disableSafeguard)
                        state = store.beginAction(state, "cleanup-preview")
                        state = store.refreshPreview(state)
                    }
                },
                onRefreshPreview = {
                    scope.launch {
                        state = store.beginAction(state, "cleanup-preview")
                        state = store.refreshPreview(state)
                    }
                },
                onExecuteCleanup = {
                    if (!window.confirm("Очистить историю запусков по текущему preview?")) {
                        return@CleanupSection
                    }
                    scope.launch {
                        state = store.beginAction(state, "cleanup-execute")
                        state = store.cleanupRunHistory(state)
                    }
                },
            )

            OutputRetentionSection(
                state = state,
                onToggleDisableSafeguard = { disableSafeguard ->
                    scope.launch {
                        state = store.updateOutputSafeguard(state, disableSafeguard)
                        state = store.beginAction(state, "output-preview")
                        state = store.refreshOutputPreview(state)
                    }
                },
                onRefreshPreview = {
                    scope.launch {
                        state = store.beginAction(state, "output-preview")
                        state = store.refreshOutputPreview(state)
                    }
                },
                onExecuteCleanup = {
                    if (!window.confirm("Очистить output-каталоги по текущему preview?")) {
                        return@OutputRetentionSection
                    }
                    scope.launch {
                        state = store.beginAction(state, "output-execute")
                        state = store.cleanupOutputs(state)
                    }
                },
            )
        },
    )
}

@Composable
private fun CleanupSection(
    state: RunHistoryCleanupPageState,
    onToggleDisableSafeguard: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onExecuteCleanup: () -> Unit,
) {
    val preview = state.preview
    val canRefresh = state.actionInProgress == null
    val canExecute = state.actionInProgress == null &&
        preview != null &&
        (preview.totalRunsToDelete > 0 || preview.totalOrphanExecutionSnapshotsToDelete > 0)
    val storageLabel = when (state.runtimeContext?.requestedMode) {
        ModuleStoreMode.DATABASE -> "База данных"
        ModuleStoreMode.FILES -> "Файлы"
        null -> "Загрузка..."
    }

    SectionCard(
        title = "Выбранный режим: $storageLabel",
        subtitle = "Очистка сохраненной истории запусков без удаления output-каталогов.",
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (!canRefresh) {
                    disabled()
                }
                onClick { onRefreshPreview() }
            }) {
                Text(if (state.actionInProgress == "cleanup-preview") "Обновляю preview..." else "Обновить preview")
            }
            Button(attrs = {
                classes("btn", "btn-outline-danger", "btn-sm")
                attr("type", "button")
                if (!canExecute) {
                    disabled()
                }
                onClick { onExecuteCleanup() }
            }) {
                Text(if (state.actionInProgress == "cleanup-execute") "Очистка..." else "Очистить историю")
            }
        },
    ) {
        Label(attrs = { classes("module-sync-cleanup-toggle") }) {
            Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                if (state.cleanupDisableSafeguard) {
                    attr("checked", "checked")
                }
                if (state.actionInProgress != null) {
                    disabled()
                }
                onChange { onToggleDisableSafeguard(!state.cleanupDisableSafeguard) }
            })
            Span({ classes("module-sync-cleanup-toggle-copy") }) {
                Text(buildSafeguardLabel(state.runtimeContext?.requestedMode))
            }
        }

        if (preview == null) {
            Div({ classes("small", "text-secondary", "mt-3") }) {
                Text("Preview очистки пока не загружен.")
            }
            return@SectionCard
        }

        Div({ classes("sync-overview-grid", "mt-3") }) {
            CleanupOverviewCard(
                label = "Retention policy",
                value = "${preview.retentionDays} дней",
                note = if (preview.safeguardEnabled) {
                    "Safeguard включен: сохраняем минимум ${preview.keepMinRunsPerModule} запусков на модуль."
                } else {
                    "Safeguard отключен: очистка пойдет только по возрасту."
                },
            )
            CleanupOverviewCard(
                label = "Текущий объем",
                value = formatBytes(preview.currentStorageBytes),
                note = buildCurrentHistoryNote(preview),
            )
            CleanupOverviewCard(
                label = "Период истории",
                value = "${preview.currentRunsCount} запусков",
                note = "${preview.currentModulesCount} модулей · ${formatInstant(preview.currentOldestRequestedAt)} .. ${formatInstant(preview.currentNewestRequestedAt)}",
            )
            CleanupOverviewCard(
                label = "Кандидаты на удаление",
                value = "${preview.totalRunsToDelete} запусков",
                note = "${preview.totalModulesAffected} модулей · cutoff ${formatInstant(preview.cutoffTimestamp)}",
            )
            CleanupOverviewCard(
                label = "Освобождение",
                value = buildCleanupFreedValue(preview),
                note = buildCleanupFreedNote(preview),
            )
            CleanupOverviewCard(
                label = "Дополнительно",
                value = preview.totalOrphanExecutionSnapshotsToDelete.toString(),
                note = buildRelatedRecordsNote(preview),
            )
        }

        if (preview.modules.isEmpty()) {
            Div({ classes("small", "text-secondary", "mt-3") }) {
                Text("По текущей policy удалять нечего.")
            }
        } else {
            Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
                Text("Затронутые модули")
            }
            Div({ classes("module-sync-cleanup-modules") }) {
                preview.modules.forEach { module ->
                    Div({ classes("module-sync-cleanup-module-row") }) {
                        Div({ classes("module-sync-cleanup-module-code") }) { Text(module.moduleCode) }
                        Div({ classes("module-sync-cleanup-module-meta") }) {
                            Text(
                                "${module.totalRunsToDelete} запусков · " +
                                    "${formatInstant(module.oldestRequestedAt)} .. ${formatInstant(module.newestRequestedAt)}",
                            )
                        }
                    }
                }
            }
        }

        if (preview.currentTopModules.isNotEmpty()) {
            Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
                Text("Самые тяжелые модули сейчас")
            }
            Div({ classes("module-sync-cleanup-modules") }) {
                preview.currentTopModules.forEach { module ->
                    CurrentTopModuleRow(module = module, includeOutputDirs = false)
                }
            }
        }
    }
}

@Composable
private fun OutputRetentionSection(
    state: RunHistoryCleanupPageState,
    onToggleDisableSafeguard: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onExecuteCleanup: () -> Unit,
) {
    val preview = state.outputPreview
    val canRefresh = state.actionInProgress == null
    val canExecute = state.actionInProgress == null &&
        preview != null &&
        (preview.totalOutputDirsToDelete > 0 || preview.totalMissingOutputDirs > 0)

    SectionCard(
        title = "Retention output-каталогов",
        subtitle = "Удаление старых файлов результатов без изменения истории запусков.",
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (!canRefresh) {
                    disabled()
                }
                onClick { onRefreshPreview() }
            }) {
                Text(if (state.actionInProgress == "output-preview") "Обновляю preview..." else "Обновить preview")
            }
            Button(attrs = {
                classes("btn", "btn-outline-danger", "btn-sm")
                attr("type", "button")
                if (!canExecute) {
                    disabled()
                }
                onClick { onExecuteCleanup() }
            }) {
                Text(if (state.actionInProgress == "output-execute") "Очистка..." else "Очистить output")
            }
        },
    ) {
        Label(attrs = { classes("module-sync-cleanup-toggle") }) {
            Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                if (state.outputDisableSafeguard) {
                    attr("checked", "checked")
                }
                if (state.actionInProgress != null) {
                    disabled()
                }
                onChange { onToggleDisableSafeguard(!state.outputDisableSafeguard) }
            })
            Span({ classes("module-sync-cleanup-toggle-copy") }) {
                Text("Отключить safeguard и не сохранять минимум ${preview?.keepMinRunsPerModule ?: 20} output-каталогов на модуль")
            }
        }

        if (preview == null) {
            Div({ classes("small", "text-secondary", "mt-3") }) {
                Text("Preview retention output-каталогов пока не загружен.")
            }
            return@SectionCard
        }

        Div({ classes("sync-overview-grid", "mt-3") }) {
            CleanupOverviewCard(
                label = "Retention policy",
                value = "${preview.retentionDays} дней",
                note = if (preview.safeguardEnabled) {
                    "Safeguard включен: сохраняем минимум ${preview.keepMinRunsPerModule} каталогов на модуль."
                } else {
                    "Safeguard отключен: удаление пойдет только по возрасту."
                },
            )
            CleanupOverviewCard(
                label = "Текущий объем",
                value = formatBytes(preview.currentBytes),
                note = "${preview.currentOutputDirs} каталогов · ${preview.currentRunsWithOutput} запусков с output.",
            )
            CleanupOverviewCard(
                label = "Период output",
                value = "${preview.currentModulesWithOutput} модулей",
                note = "${formatInstant(preview.currentOldestRequestedAt)} .. ${formatInstant(preview.currentNewestRequestedAt)}",
            )
            CleanupOverviewCard(
                label = "Каталоги",
                value = preview.totalOutputDirsToDelete.toString(),
                note = "${preview.totalModulesAffected} модулей · cutoff ${formatInstant(preview.cutoffTimestamp)}",
            )
            CleanupOverviewCard(
                label = "Освободится",
                value = formatBytes(preview.totalBytesToFree),
                note = "${preview.totalRunsAffected} запусков с output-каталогами в preview.",
            )
            CleanupOverviewCard(
                label = "Отсутствуют на диске",
                value = preview.totalMissingOutputDirs.toString(),
                note = "История останется, удаляются только реальные каталоги результата.",
            )
        }

        if (preview.modules.isEmpty()) {
            Div({ classes("small", "text-secondary", "mt-3") }) {
                Text("По текущей policy удалять нечего.")
            }
        } else {
            Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
                Text("Затронутые модули")
            }
            Div({ classes("module-sync-cleanup-modules") }) {
                preview.modules.forEach { module ->
                    Div({ classes("module-sync-cleanup-module-row") }) {
                        Div({ classes("module-sync-cleanup-module-code") }) { Text(module.moduleCode) }
                        Div({ classes("module-sync-cleanup-module-meta") }) {
                            Text(
                                "${module.totalRunsAffected} запусков · " +
                                    "${module.totalOutputDirsToDelete} каталогов · " +
                                    "${formatBytes(module.totalBytesToFree)} · " +
                                    "${formatInstant(module.oldestRequestedAt)} .. ${formatInstant(module.newestRequestedAt)}",
                            )
                        }
                    }
                }
            }
        }

        if (preview.currentTopModules.isNotEmpty()) {
            Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
                Text("Самые тяжелые модули сейчас")
            }
            Div({ classes("module-sync-cleanup-modules") }) {
                preview.currentTopModules.forEach { module ->
                    CurrentTopModuleRow(module = module, includeOutputDirs = true)
                }
            }
        }
    }
}

@Composable
private fun CleanupOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("sync-overview-card") }) {
        Div({ classes("sync-overview-label") }) { Text(label) }
        Div({ classes("sync-overview-value") }) { Text(value) }
        P({ classes("sync-overview-note", "mb-0") }) { Text(note) }
    }
}

@Composable
private fun CurrentTopModuleRow(
    module: CurrentStorageModuleResponse,
    includeOutputDirs: Boolean,
) {
    Div({ classes("module-sync-cleanup-module-row") }) {
        Div({ classes("module-sync-cleanup-module-code") }) { Text(module.moduleCode) }
        Div({ classes("module-sync-cleanup-module-meta") }) {
            val outputPart = if (includeOutputDirs) {
                " · ${module.currentOutputDirs ?: 0} каталогов"
            } else {
                ""
            }
            Text(
                "${formatBytes(module.currentStorageBytes)} · " +
                    "${module.currentRunsCount} запусков$outputPart · " +
                    "${formatInstant(module.oldestRequestedAt)} .. ${formatInstant(module.newestRequestedAt)}",
            )
        }
    }
}

private fun buildSubtitle(storageMode: ModuleStoreMode?): String =
    when (storageMode) {
        ModuleStoreMode.DATABASE ->
            "Cleanup истории и output-каталогов DB-запусков по retention policy с preview перед удалением."
        ModuleStoreMode.FILES ->
            "Cleanup истории и output-каталогов файловых запусков по retention policy."
        null ->
            "Очистка истории запусков и output-каталогов в зависимости от выбранного режима UI."
    }

private fun buildFallbackWarning(runtimeContext: com.sbrf.lt.platform.composeui.model.RuntimeContext): String =
    buildString {
        append("Выбран режим базы данных, но он сейчас недоступен.")
        runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
            append(" Причина: ")
            append(reason)
        }
        append(" Экран показывает состояние выбранного режима, а операции для БД будут недоступны до восстановления подключения.")
    }

private fun buildSafeguardLabel(storageMode: ModuleStoreMode?): String =
    when (storageMode) {
        ModuleStoreMode.DATABASE,
        ModuleStoreMode.FILES,
        null,
        -> "Отключить safeguard и не сохранять минимум 30 запусков на модуль"
    }

private fun buildRelatedRecordsNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "Source results: ${preview.totalSourceResultsToDelete} · artifacts: ${preview.totalArtifactsToDelete} · events: ${preview.totalEventsToDelete}"
    } else {
        "Для FILES удаляются встроенные события из сохраненных snapshot-ов."
    }

private fun buildCurrentHistoryNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "Физический размер history-таблиц PostgreSQL в текущем режиме."
    } else {
        "Размер persisted history файла run-state.json."
    }

private fun buildCleanupFreedValue(preview: RunHistoryCleanupPreviewResponse): String =
    preview.estimatedBytesToFree?.let(::formatBytes) ?: "Через VACUUM"

private fun buildCleanupFreedNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "DELETE уменьшит данные логически, а физическое место PostgreSQL вернет после autovacuum/VACUUM."
    } else {
        "Оценка уменьшения persisted history после cleanup preview."
    }

private fun formatInstant(value: String?): String =
    value?.replace("T", " ")?.removeSuffix("Z") ?: "—"

private fun formatBytes(value: Long): String =
    when {
        value >= 1024L * 1024L * 1024L -> "${value / (1024L * 1024L * 1024L)} ГБ"
        value >= 1024L * 1024L -> "${value / (1024L * 1024L)} МБ"
        value >= 1024L -> "${value / 1024L} КБ"
        else -> "$value Б"
    }

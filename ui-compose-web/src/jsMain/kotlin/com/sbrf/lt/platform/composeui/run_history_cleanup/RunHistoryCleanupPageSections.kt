package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.label
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun CleanupSection(
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
    val storageLabel = state.runtimeContext?.requestedMode?.label ?: "Загрузка..."

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
            Input(type = InputType.Checkbox, attrs = {
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
internal fun OutputRetentionSection(
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
            Input(type = InputType.Checkbox, attrs = {
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
internal fun CleanupOverviewCard(
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
internal fun CurrentTopModuleRow(
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

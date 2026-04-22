package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatByteSize
import com.sbrf.lt.platform.composeui.foundation.format.formatCompactDateTime
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
            RunHistoryCleanupActionButton(
                label = if (state.actionInProgress == "cleanup-preview") "Обновляю preview..." else "Обновить preview",
                toneClass = "btn-outline-secondary",
                enabled = canRefresh,
                onClick = onRefreshPreview,
            )
            RunHistoryCleanupActionButton(
                label = if (state.actionInProgress == "cleanup-execute") "Очистка..." else "Очистить историю",
                toneClass = "btn-outline-danger",
                enabled = canExecute,
                onClick = onExecuteCleanup,
            )
        },
    ) {
        RunHistoryCleanupSafeguardToggle(
            checked = state.cleanupDisableSafeguard,
            disabled = state.actionInProgress != null,
            label = buildSafeguardLabel(state.runtimeContext?.requestedMode),
            onToggle = { onToggleDisableSafeguard(!state.cleanupDisableSafeguard) },
        )

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
                value = formatByteSize(preview.currentStorageBytes),
                note = buildCurrentHistoryNote(preview),
            )
            CleanupOverviewCard(
                label = "Период истории",
                value = "${preview.currentRunsCount} запусков",
                note = "${preview.currentModulesCount} модулей · ${formatCompactDateTime(preview.currentOldestRequestedAt)} .. ${formatCompactDateTime(preview.currentNewestRequestedAt)}",
            )
            CleanupOverviewCard(
                label = "Кандидаты на удаление",
                value = "${preview.totalRunsToDelete} запусков",
                note = "${preview.totalModulesAffected} модулей · cutoff ${formatCompactDateTime(preview.cutoffTimestamp)}",
            )
            CleanupOverviewCard(
                label = "Освобождение",
                value = preview.estimatedBytesToFree?.let(::formatByteSize) ?: "Через VACUUM",
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
            RunHistoryCleanupModuleList(
                title = "Затронутые модули",
                rows = preview.modules.map { module ->
                    module.moduleCode to (
                        "${module.totalRunsToDelete} запусков · " +
                            "${formatCompactDateTime(module.oldestRequestedAt)} .. ${formatCompactDateTime(module.newestRequestedAt)}"
                        )
                },
            )
        }

        if (preview.currentTopModules.isNotEmpty()) {
            RunHistoryCurrentTopModules(
                title = "Самые тяжелые модули сейчас",
                modules = preview.currentTopModules,
                includeOutputDirs = false,
            )
        }
    }
}

@Composable
internal fun RunHistoryCleanupSafeguardToggle(
    checked: Boolean,
    disabled: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Label(attrs = { classes("module-sync-cleanup-toggle") }) {
        Input(type = InputType.Checkbox, attrs = {
            if (checked) {
                attr("checked", "checked")
            }
            if (disabled) {
                disabled()
            }
            onChange { onToggle() }
        })
        Span({ classes("module-sync-cleanup-toggle-copy") }) {
            Text(label)
        }
    }
}

@Composable
internal fun RunHistoryCleanupActionButton(
    label: String,
    toneClass: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "btn-sm")
        attr("type", "button")
        if (!enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

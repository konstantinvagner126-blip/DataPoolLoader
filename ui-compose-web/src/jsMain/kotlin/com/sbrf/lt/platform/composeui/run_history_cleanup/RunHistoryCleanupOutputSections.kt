package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatByteSize
import com.sbrf.lt.platform.composeui.foundation.format.formatCompactDateTime
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

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
            RunHistoryCleanupActionButton(
                label = if (state.actionInProgress == "output-preview") "Обновляю preview..." else "Обновить preview",
                toneClass = "btn-outline-secondary",
                enabled = canRefresh,
                onClick = onRefreshPreview,
            )
            RunHistoryCleanupActionButton(
                label = if (state.actionInProgress == "output-execute") "Очистка..." else "Очистить output",
                toneClass = "btn-outline-danger",
                enabled = canExecute,
                onClick = onExecuteCleanup,
            )
        },
    ) {
        RunHistoryCleanupSafeguardToggle(
            checked = state.outputDisableSafeguard,
            disabled = state.actionInProgress != null,
            label = "Отключить safeguard и не сохранять минимум ${preview?.keepMinRunsPerModule ?: 20} output-каталогов на модуль",
            onToggle = { onToggleDisableSafeguard(!state.outputDisableSafeguard) },
        )

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
                value = formatByteSize(preview.currentBytes),
                note = "${preview.currentOutputDirs} каталогов · ${preview.currentRunsWithOutput} запусков с output.",
            )
            CleanupOverviewCard(
                label = "Период output",
                value = "${preview.currentModulesWithOutput} модулей",
                note = "${formatCompactDateTime(preview.currentOldestRequestedAt)} .. ${formatCompactDateTime(preview.currentNewestRequestedAt)}",
            )
            CleanupOverviewCard(
                label = "Каталоги",
                value = preview.totalOutputDirsToDelete.toString(),
                note = "${preview.totalModulesAffected} модулей · cutoff ${formatCompactDateTime(preview.cutoffTimestamp)}",
            )
            CleanupOverviewCard(
                label = "Освободится",
                value = formatByteSize(preview.totalBytesToFree),
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
            RunHistoryCleanupModuleList(
                title = "Затронутые модули",
                rows = preview.modules.map { module ->
                    module.moduleCode to (
                        "${module.totalRunsAffected} запусков · " +
                            "${module.totalOutputDirsToDelete} каталогов · " +
                            "${formatByteSize(module.totalBytesToFree)} · " +
                            "${formatCompactDateTime(module.oldestRequestedAt)} .. ${formatCompactDateTime(module.newestRequestedAt)}"
                        )
                },
            )
        }

        if (preview.currentTopModules.isNotEmpty()) {
            RunHistoryCurrentTopModules(
                title = "Самые тяжелые модули сейчас",
                modules = preview.currentTopModules,
                includeOutputDirs = true,
            )
        }
    }
}

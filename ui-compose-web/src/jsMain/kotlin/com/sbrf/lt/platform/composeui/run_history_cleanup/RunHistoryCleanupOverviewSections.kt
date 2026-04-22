package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatByteSize
import com.sbrf.lt.platform.composeui.foundation.format.formatCompactDateTime
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

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
internal fun RunHistoryCleanupModuleList(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
        Text(title)
    }
    Div({ classes("module-sync-cleanup-modules") }) {
        rows.forEach { (code, meta) ->
            Div({ classes("module-sync-cleanup-module-row") }) {
                Div({ classes("module-sync-cleanup-module-code") }) { Text(code) }
                Div({ classes("module-sync-cleanup-module-meta") }) { Text(meta) }
            }
        }
    }
}

@Composable
internal fun RunHistoryCurrentTopModules(
    title: String,
    modules: List<CurrentStorageModuleResponse>,
    includeOutputDirs: Boolean,
) {
    Div({ classes("small", "text-secondary", "mt-3", "mb-2") }) {
        Text(title)
    }
    Div({ classes("module-sync-cleanup-modules") }) {
        modules.forEach { module ->
            CurrentTopModuleRow(module = module, includeOutputDirs = includeOutputDirs)
        }
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
                "${formatByteSize(module.currentStorageBytes)} · " +
                    "${module.currentRunsCount} запусков$outputPart · " +
                    "${formatCompactDateTime(module.oldestRequestedAt)} .. ${formatCompactDateTime(module.newestRequestedAt)}",
            )
        }
    }
}

package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleToolHeader(
    state: SqlConsolePageState,
    workspaceId: String,
    isRunning: Boolean,
    pendingManualTransaction: Boolean,
) {
    Div({ classes("sql-tool-header") }) {
        Div({ classes("sql-tool-identity") }) {
            SqlConsoleToolIcon()
            Div({ classes("sql-tool-copy") }) {
                Div({ classes("sql-tool-nav") }) {
                    A(attrs = {
                        classes("sql-tool-nav-action")
                        href("/")
                    }) { Text("На главную") }
                    A(attrs = {
                        classes("sql-tool-nav-action")
                        href(buildSqlConsoleObjectsWorkspaceHref(workspaceId))
                    }) { Text("Объекты БД") }
                    Span({ classes("sql-tool-nav-action", "active") }) { Text("SQL-консоль") }
                }
                Div({ classes("sql-tool-eyebrow") }) { Text("Load Testing Data Platform") }
                Div({ classes("sql-tool-title") }) { Text("SQL-консоль по источникам") }
            }
        }

        Div({ classes("sql-tool-status") }) {
            SqlToolHeaderChip(
                label = "Источники",
                value = buildSelectedSourcesSummary(state.selectedSourceNames),
                tone = if (state.selectedSourceNames.isEmpty()) "warn" else "accent",
            )
            SqlToolHeaderChip(
                label = "Workspace",
                value = workspaceId.takeLast(10),
                tone = "neutral",
            )
            SqlToolHeaderChip(
                label = "Режим",
                value = translateTransactionMode(state.transactionMode),
                tone = when {
                    pendingManualTransaction -> "warn"
                    state.transactionMode.equals("AUTO_COMMIT", ignoreCase = true) -> "ok"
                    else -> "accent"
                },
            )
            SqlToolHeaderChip(
                label = "Подключения",
                value = buildSqlToolConnectionStatus(state.sourceStatuses),
                tone = buildSqlToolConnectionTone(state.sourceStatuses),
            )
            if (isRunning) {
                SqlToolHeaderChip(label = "Выполнение", value = "выполняется", tone = "accent")
            } else if (pendingManualTransaction) {
                SqlToolHeaderChip(label = "Выполнение", value = "ожидает Commit", tone = "warn")
            }
        }
    }
}

@Composable
private fun SqlConsoleToolIcon() {
    Div({
        classes("sql-tool-icon")
        attr("aria-hidden", "true")
    }) {
        Span({ classes("sql-tool-icon-stack", "sql-tool-icon-stack-back") })
        Span({ classes("sql-tool-icon-stack", "sql-tool-icon-stack-front") })
        Span({ classes("sql-tool-icon-query") }) { Text("SQL") }
    }
}

@Composable
private fun SqlToolHeaderChip(
    label: String,
    value: String,
    tone: String,
) {
    Span({ classes("sql-tool-chip", tone) }) {
        Span({ classes("sql-tool-chip-label") }) { Text(label) }
        Span({ classes("sql-tool-chip-value") }) { Text(value) }
    }
}

private fun buildSqlToolConnectionStatus(statuses: List<SqlConsoleSourceConnectionStatus>): String {
    if (statuses.isEmpty()) {
        return "не проверялись"
    }
    val okCount = statuses.count { it.status.equals("OK", ignoreCase = true) }
    val failedCount = statuses.count { it.status.equals("FAILED", ignoreCase = true) }
    return when {
        failedCount == 0 -> "$okCount OK"
        okCount == 0 -> "$failedCount ошибка"
        else -> "$okCount OK / $failedCount ошибка"
    }
}

private fun buildSqlToolConnectionTone(statuses: List<SqlConsoleSourceConnectionStatus>): String {
    if (statuses.isEmpty()) {
        return "neutral"
    }
    val failedCount = statuses.count { it.status.equals("FAILED", ignoreCase = true) }
    return when {
        failedCount == 0 -> "ok"
        failedCount == statuses.size -> "danger"
        else -> "warn"
    }
}

package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleExecutionHistoryBlock(
    history: List<SqlConsoleExecutionHistoryEntry>,
    onApply: (SqlConsoleExecutionHistoryEntry) -> Unit,
    onRepeat: (SqlConsoleExecutionHistoryEntry) -> Unit,
) {
    if (history.isEmpty()) {
        return
    }
    Div({ classes("sql-history-block") }) {
        Div({ classes("sql-history-block-head") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("История выполнения этой вкладки") }
                Div({ classes("small", "text-secondary") }) {
                    Text("Последние execution session текущего workspace: SQL, набор source, итог и длительность.")
                }
            }
            Div({ classes("sql-query-library-summary-chip") }) {
                Div({ classes("sql-query-library-summary-chip-label") }) { Text("Sessions") }
                Div({ classes("sql-query-library-summary-chip-value") }) { Text(history.size.toString()) }
            }
        }
        Div({ classes("sql-history-list") }) {
            history.take(6).forEach { entry ->
                SqlConsoleExecutionHistoryEntryCard(
                    entry = entry,
                    onApply = { onApply(entry) },
                    onRepeat = { onRepeat(entry) },
                )
            }
        }
    }
}

@Composable
private fun SqlConsoleExecutionHistoryEntryCard(
    entry: SqlConsoleExecutionHistoryEntry,
    onApply: () -> Unit,
    onRepeat: () -> Unit,
) {
    Div({ classes("sql-history-entry") }) {
        Div({ classes("sql-history-entry-head") }) {
            Div({ classes("sql-history-entry-statuses") }) {
                Div({ classes("sql-history-status", executionHistoryStatusClass(entry)) }) {
                    Text(buildExecutionHistoryOutcomeText(entry))
                }
                Div({ classes("sql-history-mode") }) {
                    Text(if (entry.autoCommitEnabled) "AUTO" else "TXN")
                }
            }
            Div({ classes("sql-history-entry-meta") }) {
                Text(buildExecutionHistoryMeta(entry))
            }
        }
        Div({ classes("sql-history-entry-sql") }) {
            Text(entry.sql.sqlHistoryPreview())
        }
        Div({ classes("sql-history-entry-sources") }) {
            Text(
                if (entry.selectedSourceNames.isEmpty()) {
                    "Источники не сохранены."
                } else {
                    "Source: ${entry.selectedSourceNames.joinToString(", ")}"
                },
            )
        }
        entry.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
            Div({ classes("sql-history-entry-error") }) { Text(errorMessage) }
        }
        Div({ classes("sql-history-entry-actions") }) {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                onClick { onApply() }
            }) {
                Text("Подставить")
            }
            Button(attrs = {
                classes("btn", "btn-outline-primary", "btn-sm")
                attr("type", "button")
                onClick { onRepeat() }
            }) {
                Text("Повторить")
            }
        }
    }
}

private fun buildExecutionHistoryOutcomeText(entry: SqlConsoleExecutionHistoryEntry): String =
    when {
        entry.transactionState == "PENDING_COMMIT" -> "PENDING COMMIT"
        entry.transactionState == "COMMITTED" -> "COMMIT"
        entry.transactionState == "ROLLED_BACK" -> "ROLLBACK"
        entry.transactionState == "ROLLED_BACK_BY_TIMEOUT" -> "ROLLBACK TTL"
        entry.transactionState == "ROLLED_BACK_BY_OWNER_LOSS" -> "ROLLBACK OWNER"
        entry.status == "SUCCESS" -> "SUCCESS"
        entry.status == "FAILED" -> "FAILED"
        entry.status == "CANCELLED" -> "CANCELLED"
        else -> entry.status
    }

private fun executionHistoryStatusClass(entry: SqlConsoleExecutionHistoryEntry): String =
    when {
        entry.transactionState == "PENDING_COMMIT" -> "sql-history-status-pending"
        entry.transactionState in setOf("ROLLED_BACK", "ROLLED_BACK_BY_TIMEOUT", "ROLLED_BACK_BY_OWNER_LOSS") ->
            "sql-history-status-rollback"
        entry.transactionState == "COMMITTED" -> "sql-history-status-commit"
        entry.status == "SUCCESS" -> "sql-history-status-success"
        entry.status == "FAILED" -> "sql-history-status-failed"
        entry.status == "CANCELLED" -> "sql-history-status-cancelled"
        else -> "sql-history-status-neutral"
    }

private fun buildExecutionHistoryMeta(entry: SqlConsoleExecutionHistoryEntry): String =
    buildString {
        append(formatDateTime(entry.startedAt))
        entry.durationMillis?.let {
            append(" • ")
            append(formatDurationMillis(it))
        }
        entry.finishedAt?.let {
            append(" • Завершение: ")
            append(formatDateTime(it))
        }
    }

private fun String.sqlHistoryPreview(maxLength: Int = 180): String {
    val normalized = replace('\n', ' ')
        .replace('\r', ' ')
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength - 1).trimEnd() + "…"
    }
}

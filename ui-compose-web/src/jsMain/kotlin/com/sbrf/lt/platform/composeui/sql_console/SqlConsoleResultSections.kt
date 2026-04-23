package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Span

@Composable
internal fun ExecutionStatusStrip(
    execution: SqlConsoleExecutionResponse?,
    runningClockTick: Int,
) {
    val isRunning = execution?.status.equals("RUNNING", ignoreCase = true)
    val showLiveDuration = isRunning && runningClockTick >= 0
    val cssClass = when {
        execution == null -> "sql-status-strip"
        execution.transactionState == "PENDING_COMMIT" -> "sql-status-strip sql-status-strip-warning"
        execution.transactionState == "ROLLED_BACK_BY_TIMEOUT" -> "sql-status-strip sql-status-strip-failed"
        execution.transactionState == "ROLLED_BACK_BY_OWNER_LOSS" -> "sql-status-strip sql-status-strip-failed"
        execution.transactionState == "ROLLED_BACK" -> "sql-status-strip sql-status-strip-warning"
        execution.transactionState == "COMMITTED" -> "sql-status-strip sql-status-strip-success"
        execution.status.equals("FAILED", ignoreCase = true) -> "sql-status-strip sql-status-strip-failed"
        execution.status.equals("SUCCESS", ignoreCase = true) -> "sql-status-strip sql-status-strip-success"
        execution.status.equals("CANCELLED", ignoreCase = true) -> "sql-status-strip sql-status-strip-warning"
        else -> "sql-status-strip sql-status-strip-running"
    }
    val text = buildExecutionStatusText(execution)
    val hint = buildExecutionStatusHint(execution)
    Div({ classesFromString(cssClass) }) {
        Div({ classes("sql-status-strip-content") }) {
            if (isRunning && execution != null) {
                Div({ classes("run-progress-status-wrap") }) {
                    Div({ classes("run-progress-spinner-arrows") }) {
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-forward") }) { Text("↻") }
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-backward") }) { Text("↺") }
                    }
                    Div({ classes("sql-status-strip-copy") }) {
                        Div({ classes("sql-status-strip-title") }) { Text(text) }
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(buildExecutionStatusMeta(execution, showLiveDuration))
                        }
                        hint?.let {
                            Div({ classes("sql-status-strip-hint") }) { Text(it) }
                        }
                    }
                }
            } else {
                Div({ classes("sql-status-strip-copy") }) {
                    Div({ classes("sql-status-strip-title") }) { Text(text) }
                    if (execution != null) {
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(buildExecutionStatusMeta(execution, showLiveDuration = false))
                        }
                    }
                    hint?.let {
                        Div({ classes("sql-status-strip-hint") }) { Text(it) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueryOutputPanel(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    activeTab: String,
    activeDataView: String,
    selectedShard: String?,
    currentPage: Int,
    onSelectTab: (String) -> Unit,
) {
    Div({ classes("sql-output-tabs") }) {
        OutputTabButton(
            label = "Данные",
            active = activeTab == "data",
            enabled = true,
            onClick = { onSelectTab("data") },
        )
        OutputTabButton(
            label = "Статусы",
            active = activeTab == "status",
            enabled = true,
            onClick = { onSelectTab("status") },
        )
    }
    OutputPane(activeTab == "data") {
        if (activeTab == "data") {
            SelectResultPane(
                execution = execution,
                result = result,
                pageSize = pageSize,
                activeDataView = activeDataView,
                selectedShard = selectedShard,
                currentPage = currentPage,
            )
        }
    }
    OutputPane(activeTab == "status") {
        if (activeTab == "status") {
            StatusResultPane(
                execution = execution,
                result = result,
            )
        }
    }
}

@Composable
internal fun OutputTabButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("sql-output-tab")
        if (active) {
            classes("active")
        }
        attr("type", "button")
        if (!enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
internal fun RenderExecutionResultPlaceholder(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    emptyText: String,
    pendingText: String,
    resultPendingLeadText: String? = null,
): Boolean {
    if (execution == null) {
        SqlResultPlaceholder(emptyText)
        return true
    }
    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            resultPendingLeadText?.let { leadText ->
                ResultMutedText(leadText)
            }
            SqlResultPlaceholder(pendingText)
        }
        return true
    }
    return false
}

@Composable
internal fun OutputPane(
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Div({
        classes("sql-output-pane")
        if (active) {
            classes("active")
        }
    }) {
        content()
    }
}

@Composable
internal fun ResultMutedText(
    text: String,
) {
    Div({ classes("small", "text-secondary", "mb-3") }) {
        Text(text)
    }
}

@Composable
internal fun SqlResultPlaceholder(
    text: String,
) {
    Div({ classes("sql-result-placeholder") }) {
        Text(text)
    }
}

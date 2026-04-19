package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

@Composable
internal fun ExecutionStatusStrip(
    execution: SqlConsoleExecutionResponse?,
    runningClockTick: Int,
) {
    val isRunning = execution?.status.equals("RUNNING", ignoreCase = true)
    val showLiveDuration = isRunning && runningClockTick >= 0
    val cssClass = when {
        execution == null -> "sql-status-strip"
        execution.status.equals("FAILED", ignoreCase = true) -> "sql-status-strip sql-status-strip-failed"
        execution.status.equals("SUCCESS", ignoreCase = true) -> "sql-status-strip sql-status-strip-success"
        execution.status.equals("CANCELLED", ignoreCase = true) -> "sql-status-strip sql-status-strip-warning"
        else -> "sql-status-strip sql-status-strip-running"
    }
    val text = buildExecutionStatusText(execution)
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
    selectedShard: String?,
    currentPage: Int,
    onSelectTab: (String) -> Unit,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
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
    Div({
        classes("sql-output-pane")
        if (activeTab == "data") {
            classes("active")
        }
    }) {
        if (activeTab == "data") {
            SelectResultPane(
                execution = execution,
                result = result,
                pageSize = pageSize,
                selectedShard = selectedShard,
                currentPage = currentPage,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
        }
    }
    Div({
        classes("sql-output-pane")
        if (activeTab == "status") {
            classes("active")
        }
    }) {
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
internal fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    selectedShard: String?,
    currentPage: Int,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    if (
        RenderExecutionResultPlaceholder(
            execution = execution,
            result = result,
            emptyText = "Пока нет данных для отображения.",
            pendingText = "Выполни запрос, чтобы увидеть данные со всех shard/source.",
            resultPendingLeadText = "Выполняется запрос...",
        )
    ) {
        return
    }
    val readyResult = requireNotNull(result)

    if (readyResult.statementType != "RESULT_SET") {
        Div({ classes("sql-result-placeholder") }) {
            Text("Команда ${readyResult.statementKeyword} не возвращает табличные данные. Смотри вкладку «Статусы».")
        }
        return
    }

    val successfulShards = readyResult.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
    if (successfulShards.isEmpty()) {
        Div({ classes("sql-result-placeholder") }) {
            Text("Ни один source не вернул данные для отображения.")
        }
        return
    }

    val activeShard = successfulShards.firstOrNull { it.shardName == selectedShard } ?: successfulShards.first()
    val totalPages = maxOf(1, (activeShard.rowCount + pageSize - 1) / pageSize)
    val normalizedPage = currentPage.coerceIn(1, totalPages)
    val startIndex = (normalizedPage - 1) * pageSize
    val endIndexExclusive = minOf(startIndex + pageSize, activeShard.rowCount)
    val visibleRows = activeShard.rows.drop(startIndex).take(pageSize)

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text("Данные показываются отдельно по каждому source. Лимит на source: ${readyResult.maxRowsPerShard}.")
    }
    Ul({ classes("nav", "nav-tabs", "sql-result-tabs", "mb-3") }) {
        successfulShards.forEach { shard ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (shard.shardName == activeShard.shardName) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onSelectShard(shard.shardName) }
                }) {
                    Text("${shard.shardName} (${shard.rowCount})")
                }
            }
        }
    }
    Div({ classes("small", "text-secondary", "mb-3") }) {
        Text(
            buildResultPageSummary(
                shard = activeShard,
                result = readyResult,
                startIndex = startIndex,
                endIndexExclusive = endIndexExclusive,
                normalizedPage = normalizedPage,
                totalPages = totalPages,
            ),
        )
    }
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "table-striped", "sql-result-table", "mb-0") }) {
            Thead {
                Tr {
                    activeShard.columns.forEach { column ->
                        Th { Text(column) }
                    }
                }
            }
            Tbody {
                visibleRows.forEach { row ->
                    Tr {
                        activeShard.columns.forEach { column ->
                            Td { Text(row[column] ?: "") }
                        }
                    }
                }
            }
        }
    }
    if (totalPages > 1) {
        Div({ classes("sql-pagination-footer") }) {
            Div({ classes("small", "text-secondary") }) {
                Text("Страница $normalizedPage из $totalPages")
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                (1..totalPages).forEach { page ->
                    Button(attrs = {
                        classes(
                            "btn",
                            "btn-sm",
                            if (page == normalizedPage) "btn-dark" else "btn-outline-secondary",
                        )
                        attr("type", "button")
                        onClick { onSelectPage(page) }
                    }) {
                        Text(page.toString())
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
) {
    if (
        RenderExecutionResultPlaceholder(
            execution = execution,
            result = result,
            emptyText = "Пока нет результатов для отображения.",
            pendingText = "Ожидается завершение запроса.",
        )
    ) {
        return
    }
    val readyExecution = requireNotNull(execution)
    val readyResult = requireNotNull(result)

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text(buildResultStatusSummary(readyExecution, readyResult))
    }
    if (readyResult.shardResults.isEmpty()) {
        EmptyStateCard(
            title = "Результаты",
            text = "Сервер не вернул результатов по выбранным shard/source.",
        )
        return
    }
    Div({ classes("table-responsive", "mb-3") }) {
        Table({ classes("table", "table-striped", "table-hover", "align-middle", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Source") }
                    Th { Text("Статус") }
                    Th { Text("Старт") }
                    Th { Text("Финиш") }
                    Th { Text("Длительность") }
                    Th { Text("Затронуто строк") }
                    Th { Text("Сообщение") }
                    Th { Text("Ошибка") }
                }
            }
            Tbody {
                readyResult.shardResults.forEach { shard ->
                    Tr {
                        Td { org.jetbrains.compose.web.dom.B { Text(shard.shardName) } }
                        Td { ShardStatusBadge(shard.status) }
                        Td { Text(formatDateTime(shard.startedAt)) }
                        Td { Text(formatDateTime(shard.finishedAt)) }
                        Td { Text(formatDuration(shard.startedAt, shard.finishedAt, running = shard.status.equals("RUNNING", ignoreCase = true))) }
                        Td { Text(shard.affectedRows?.toString() ?: "-") }
                        Td { Text(shard.message ?: "-") }
                        Td { Text(shard.errorMessage ?: "-") }
                    }
                }
            }
        }
    }
    Div({ classes("sql-shard-card-grid") }) {
        readyResult.shardResults.forEach { shard ->
            Div({ classes("sql-shard-card", "status-${sourceStatusSuffix(shard.status)}") }) {
                Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3") }) {
                    Div {
                        H3({ classes("h6", "mb-1") }) { Text(shard.shardName) }
                        Div({ classes("small", "text-secondary") }) {
                            Text(buildShardStatusSummary(shard))
                        }
                    }
                    ShardStatusBadge(shard.status)
                }
                if (!shard.errorMessage.isNullOrBlank()) {
                    AlertBanner(shard.errorMessage ?: "", "danger")
                } else if (!shard.message.isNullOrBlank()) {
                    Div({ classes("alert", "alert-secondary", "mt-3", "mb-0") }) {
                        Text(shard.message ?: "")
                    }
                }
                Div({ classes("sql-shard-card-timings") }) {
                    Div { Text("Старт: ${formatDateTime(shard.startedAt)}") }
                    Div { Text("Финиш: ${formatDateTime(shard.finishedAt)}") }
                    Div { Text("Длительность: ${buildShardDurationText(shard)}") }
                }
            }
        }
    }
}

@Composable
private fun ShardStatusBadge(status: String) {
    StatusBadge(
        text = translateSourceStatus(status),
        tone = sourceStatusBadgeTone(status),
    )
}

@Composable
private fun RenderExecutionResultPlaceholder(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    emptyText: String,
    pendingText: String,
    resultPendingLeadText: String? = null,
): Boolean {
    if (execution == null) {
        Div({ classes("sql-result-placeholder") }) {
            Text(emptyText)
        }
        return true
    }
    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            resultPendingLeadText?.let { leadText ->
                Div({ classes("text-secondary", "small", "mb-3") }) {
                    Text(leadText)
                }
            }
            Div({ classes("sql-result-placeholder") }) {
                Text(pendingText)
            }
        }
        return true
    }
    return false
}

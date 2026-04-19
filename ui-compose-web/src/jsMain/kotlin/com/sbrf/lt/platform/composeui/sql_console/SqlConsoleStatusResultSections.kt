package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

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

    ResultMutedText(buildResultStatusSummary(readyExecution, readyResult))
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
internal fun ShardStatusBadge(status: String) {
    StatusBadge(
        text = translateSourceStatus(status),
        tone = sourceStatusBadgeTone(status),
    )
}

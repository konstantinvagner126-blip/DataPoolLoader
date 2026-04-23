package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import org.jetbrains.compose.web.dom.Div
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
}

@Composable
internal fun ShardStatusBadge(status: String) {
    StatusBadge(
        text = translateSourceStatus(status),
        tone = sourceStatusBadgeTone(status),
    )
}

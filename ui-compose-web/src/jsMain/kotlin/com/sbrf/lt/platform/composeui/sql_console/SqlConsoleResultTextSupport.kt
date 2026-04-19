package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis

internal fun buildExecutionStatusText(execution: SqlConsoleExecutionResponse?): String =
    when {
        execution == null -> "Запрос пока не выполнялся."
        execution.status.equals("RUNNING", ignoreCase = true) && execution.cancelRequested ->
            "Запрос выполняется, отправлена команда на остановку."
        execution.status.equals("RUNNING", ignoreCase = true) ->
            "Сценарий выполняется."
        execution.transactionState == "PENDING_COMMIT" ->
            "Сценарий выполнен и ждет команды Коммит или Роллбек."
        execution.transactionState == "COMMITTED" ->
            "Транзакция зафиксирована."
        execution.transactionState == "ROLLED_BACK" ->
            "Транзакция откатана."
        execution.status.equals("SUCCESS", ignoreCase = true) ->
            "Запрос завершен успешно."
        execution.status.equals("FAILED", ignoreCase = true) ->
            execution.errorMessage ?: "Запрос завершился ошибкой."
        execution.status.equals("CANCELLED", ignoreCase = true) ->
            "Запрос остановлен."
        else -> "Статус запроса: ${execution.status}."
    }

internal fun buildExecutionStatusMeta(
    execution: SqlConsoleExecutionResponse,
    showLiveDuration: Boolean,
): String =
    buildString {
        append("Старт: ")
        append(formatDateTime(execution.startedAt))
        if (showLiveDuration) {
            append(" • Прошло: ")
            append(formatDuration(execution.startedAt, execution.finishedAt, running = true))
        } else {
            if (execution.transactionState == "PENDING_COMMIT" && execution.transactionShardNames.isNotEmpty()) {
                append(" • Открытых транзакций: ")
                append(execution.transactionShardNames.joinToString(", "))
            }
            if (!execution.finishedAt.isNullOrBlank()) {
                append(" • Завершение: ")
                append(formatDateTime(execution.finishedAt))
                append(" • Длительность: ")
                append(formatDuration(execution.startedAt, execution.finishedAt))
            }
        }
    }

internal fun buildResultPageSummary(
    shard: SqlConsoleShardResult,
    result: SqlConsoleQueryResult,
    startIndex: Int,
    endIndexExclusive: Int,
    normalizedPage: Int,
    totalPages: Int,
): String =
    buildString {
        append("Source ")
        append(shard.shardName)
        append(". Показано строк: ")
        append(if (shard.rowCount == 0) 0 else startIndex + 1)
        append("-")
        append(endIndexExclusive)
        append(" из ")
        append(shard.rowCount)
        append(". Страница ")
        append(normalizedPage)
        append(" из ")
        append(totalPages)
        append(".")
        if (shard.truncated) {
            append(" Результат усечен лимитом ${result.maxRowsPerShard} строк на source.")
        }
    }

internal fun buildResultStatusSummary(
    execution: SqlConsoleExecutionResponse,
    result: SqlConsoleQueryResult,
): String =
    buildString {
        append("Тип команды: ")
        append(result.statementKeyword)
        append(" • shard/source: ")
        append(result.shardResults.size)
        append(" • startedAt: ")
        append(execution.startedAt)
        if (!execution.finishedAt.isNullOrBlank()) {
            append(" • finishedAt: ")
            append(execution.finishedAt.orEmpty())
        }
    }

internal fun buildShardStatusSummary(shard: SqlConsoleShardResult): String =
    buildString {
        append("Статус: ")
        append(shard.status)
        if (shard.affectedRows != null) {
            append(" • affectedRows: ")
            append(shard.affectedRows)
        }
        if (shard.rowCount > 0) {
            append(" • rows: ")
            append(shard.rowCount)
        }
        if (shard.durationMillis != null) {
            append(" • длительность: ")
            append(formatDurationMillis(shard.durationMillis))
        } else if (!shard.startedAt.isNullOrBlank()) {
            append(" • старт: ")
            append(formatDateTime(shard.startedAt))
        }
        if (shard.truncated) {
            append(" • результат усечен")
        }
    }

internal fun buildShardDurationText(shard: SqlConsoleShardResult): String =
    if (shard.durationMillis != null) {
        formatDurationMillis(shard.durationMillis)
    } else {
        formatDuration(
            shard.startedAt,
            shard.finishedAt,
            running = shard.status.equals("RUNNING", ignoreCase = true),
        )
    }

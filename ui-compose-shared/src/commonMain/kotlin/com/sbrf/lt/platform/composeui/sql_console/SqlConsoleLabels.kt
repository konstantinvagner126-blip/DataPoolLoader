package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse

fun buildConsoleInfoText(info: SqlConsoleInfo?): String =
    when {
        info == null -> "Конфигурация не загружена."
        !info.configured -> "SQL-консоль не настроена. Проверь конфигурацию источников и credential.properties."
        else -> "Доступно источников: ${info.sourceNames.size}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
    }

fun buildConnectionCheckStatusText(result: SqlConsoleConnectionCheckResponse): String {
    val success = result.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("OK", ignoreCase = true) }
    val failed = result.sourceResults.size - success
    return "Проверка подключений завершена. Успешно: $success, с ошибкой: $failed."
}

fun translateConnectionStatus(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "Доступен"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "Недоступен"
        status.equals("RUNNING", ignoreCase = true) -> "Проверяется"
        else -> status
    }

fun sourceStatusTone(status: SqlConsoleSourceConnectionStatus?): String =
    when {
        status == null -> "unknown"
        status.status.equals("SUCCESS", ignoreCase = true) || status.status.equals("OK", ignoreCase = true) -> "success"
        status.status.equals("FAILED", ignoreCase = true) || status.status.equals("ERROR", ignoreCase = true) -> "failed"
        else -> "unknown"
    }

fun translateSourceStatus(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "Успешно"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "Ошибка"
        status.equals("RUNNING", ignoreCase = true) -> "Выполняется"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "С предупреждениями"
        else -> status
    }

fun runButtonTone(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
): String =
    when {
        analysis.keyword == "SQL" -> "primary"
        strictSafetyEnabled && !analysis.readOnly -> "danger"
        analysis.dangerous -> "danger"
        !analysis.readOnly -> "warning"
        else -> "primary"
    }

fun sourceStatusSuffix(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "success"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "failed"
        status.equals("RUNNING", ignoreCase = true) -> "running"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "success_with_warnings"
        else -> "not_enabled"
    }

fun sourceStatusBadgeTone(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "success"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "danger"
        status.equals("RUNNING", ignoreCase = true) -> "primary"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "warning"
        else -> "secondary"
    }

fun buildCredentialsStatusText(status: CredentialsStatusResponse): String {
    val sourceLabel = when {
        status.uploaded -> "загружен через UI"
        status.mode.equals("FILE", ignoreCase = true) -> "файл по умолчанию"
        else -> "файл не задан"
    }
    val availability = if (status.fileAvailable) "доступен" else "не найден"
    return "$sourceLabel: ${status.displayName} ($availability)"
}

package com.sbrf.lt.platform.composeui.sql_console

internal fun buildConsoleInfoText(info: SqlConsoleInfo?): String =
    when {
        info == null -> "Конфигурация не загружена."
        !info.configured -> "SQL-консоль не настроена. Проверь конфигурацию источников и credential.properties."
        else -> "Доступно источников: ${info.sourceNames.size}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
    }

internal fun buildConnectionCheckStatusText(result: SqlConsoleConnectionCheckResponse): String {
    val success = result.sourceResults.count {
        it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("OK", ignoreCase = true)
    }
    val failed = result.sourceResults.size - success
    return "Проверка подключений завершена. Успешно: $success, с ошибкой: $failed."
}

internal fun sourceStatusCardClass(status: SqlConsoleSourceConnectionStatus?): String =
    "sql-source-checkbox-${sourceStatusTone(status)}"

internal fun Boolean?.orFalse(): Boolean = this == true

internal fun buildRunButtonClass(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
): String = "btn-${runButtonTone(analysis, strictSafetyEnabled)}"

internal fun statusCssSuffix(status: String): String =
    sourceStatusSuffix(status)

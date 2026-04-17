package com.sbrf.lt.platform.ui.model

/**
 * Команда экспорта результата SQL-консоли в CSV или ZIP.
 */
data class SqlConsoleExportRequest(
    val result: SqlConsoleQueryResponse,
    val shardName: String? = null,
)

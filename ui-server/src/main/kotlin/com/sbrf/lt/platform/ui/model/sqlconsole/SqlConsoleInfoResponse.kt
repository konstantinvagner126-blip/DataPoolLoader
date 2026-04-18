package com.sbrf.lt.platform.ui.model

/**
 * Сводная информация о конфигурации SQL-консоли.
 */
data class SqlConsoleInfoResponse(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

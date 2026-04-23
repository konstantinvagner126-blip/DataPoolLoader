package com.sbrf.lt.platform.ui.model

/**
 * Сводная информация о конфигурации SQL-консоли.
 */
data class SqlConsoleInfoResponse(
    val configured: Boolean,
    val sourceNames: List<String>,
    val sourceGroups: List<SqlConsoleSourceGroupResponse> = emptyList(),
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

data class SqlConsoleSourceGroupResponse(
    val name: String,
    val sourceNames: List<String>,
)

package com.sbrf.lt.platform.ui.model

/**
 * Сводная информация о конфигурации SQL-консоли.
 */
data class SqlConsoleInfoResponse(
    val configured: Boolean,
    val sourceCatalog: List<SqlConsoleSourceCatalogEntryResponse> = emptyList(),
    val groups: List<SqlConsoleSourceGroupResponse> = emptyList(),
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

data class SqlConsoleSourceCatalogEntryResponse(
    val name: String,
)

data class SqlConsoleSourceGroupResponse(
    val name: String,
    val sources: List<String>,
    val synthetic: Boolean,
)

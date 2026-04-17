package com.sbrf.lt.platform.ui.model

/**
 * Команда изменения серверных настроек SQL-консоли.
 */
data class SqlConsoleSettingsUpdateRequest(
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int? = null,
)

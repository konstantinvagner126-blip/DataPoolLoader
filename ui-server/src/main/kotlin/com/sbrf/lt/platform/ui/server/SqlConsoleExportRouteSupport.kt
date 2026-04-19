package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest

internal fun SqlConsoleExportRequest.requireShardName(): String =
    shardName?.trim()?.takeIf { it.isNotEmpty() }
        ?: badRequest("Для CSV-экспорта нужно указать shardName.")

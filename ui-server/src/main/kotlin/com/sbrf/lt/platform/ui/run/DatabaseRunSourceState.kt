package com.sbrf.lt.platform.ui.run

/**
 * Текущее состояние source-результата внутри выполняющегося DB-run.
 */
internal data class DatabaseRunSourceState(
    var status: String = "PENDING",
    var exportedRowCount: Long? = null,
    var mergedRowCount: Long? = null,
)

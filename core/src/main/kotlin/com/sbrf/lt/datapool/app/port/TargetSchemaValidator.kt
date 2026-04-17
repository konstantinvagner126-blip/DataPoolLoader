package com.sbrf.lt.datapool.app.port

import com.sbrf.lt.datapool.model.TargetConfig

/**
 * Порт предварительной проверки совместимости входных колонок и целевой таблицы.
 */
fun interface TargetSchemaValidator {
    fun validate(
        target: TargetConfig,
        resolvedJdbcUrl: String,
        resolvedUsername: String,
        resolvedPassword: String,
        incomingColumns: List<String>,
    )
}

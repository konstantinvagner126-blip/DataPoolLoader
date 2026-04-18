package com.sbrf.lt.platform.ui.model

/**
 * Полное состояние визуальной формы `application.yml`.
 */
data class ConfigFormStateResponse(
    val outputDir: String,
    val fileFormat: String,
    val mergeMode: String,
    val errorMode: String,
    val parallelism: Int,
    val fetchSize: Int,
    val queryTimeoutSec: Int?,
    val progressLogEveryRows: Long,
    val maxMergedRows: Long?,
    val deleteOutputFilesAfterCompletion: Boolean,
    val commonSql: String,
    val commonSqlFile: String?,
    val sources: List<ConfigFormSourceState> = emptyList(),
    val quotas: List<ConfigFormQuotaState> = emptyList(),
    val targetEnabled: Boolean,
    val targetJdbcUrl: String,
    val targetUsername: String,
    val targetPassword: String,
    val targetTable: String,
    val targetTruncateBeforeLoad: Boolean,
    val warnings: List<String> = emptyList(),
)

package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.serialization.Serializable

@Serializable
data class ConfigFormParseRequestDto(
    val configText: String,
)

@Serializable
data class ConfigFormSourceStateDto(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val sql: String? = null,
    val sqlFile: String? = null,
)

@Serializable
data class ConfigFormQuotaStateDto(
    val source: String,
    val percent: Double? = null,
)

@Serializable
data class ConfigFormStateDto(
    val outputDir: String,
    val fileFormat: String,
    val mergeMode: String,
    val errorMode: String,
    val parallelism: Int,
    val fetchSize: Int,
    val queryTimeoutSec: Int? = null,
    val progressLogEveryRows: Long,
    val maxMergedRows: Long? = null,
    val deleteOutputFilesAfterCompletion: Boolean,
    val commonSql: String,
    val commonSqlFile: String? = null,
    val sources: List<ConfigFormSourceStateDto> = emptyList(),
    val quotas: List<ConfigFormQuotaStateDto> = emptyList(),
    val targetEnabled: Boolean,
    val targetJdbcUrl: String,
    val targetUsername: String,
    val targetPassword: String,
    val targetTable: String,
    val targetTruncateBeforeLoad: Boolean,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ConfigFormUpdateRequestDto(
    val configText: String,
    val formState: ConfigFormStateDto,
)

@Serializable
data class ConfigFormUpdateResponseDto(
    val configText: String,
    val formState: ConfigFormStateDto,
)

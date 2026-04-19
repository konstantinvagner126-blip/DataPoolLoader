package com.sbrf.lt.platform.composeui.module_editor

internal fun updateSource(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormSourceStateDto.() -> ConfigFormSourceStateDto,
): ConfigFormStateDto =
    formState.copy(
        sources = formState.sources.mapIndexed { sourceIndex, source ->
            if (sourceIndex == index) {
                source.transform()
            } else {
                source
            }
        },
    )

internal fun updateQuota(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormQuotaStateDto.() -> ConfigFormQuotaStateDto,
): ConfigFormStateDto =
    formState.copy(
        quotas = formState.quotas.mapIndexed { quotaIndex, quota ->
            if (quotaIndex == index) {
                quota.transform()
            } else {
                quota
            }
        },
    )

internal data class SqlResourceOption(
    val label: String,
    val path: String,
    val exists: Boolean,
)

internal data class DefaultSqlState(
    val mode: String,
    val inlineText: String,
    val catalogPath: String,
    val externalRef: String? = null,
)

internal data class SourceSqlState(
    val mode: String,
    val inlineText: String,
    val catalogPath: String,
    val externalRef: String? = null,
    val summary: String,
)

internal fun catalogLabel(resource: SqlResourceOption): String =
    if (resource.exists) {
        resource.path
    } else {
        "[Отсутствует] ${resource.path}"
    }

internal fun buildDefaultSqlState(
    formState: ConfigFormStateDto,
    sqlResources: List<SqlResourceOption>,
): DefaultSqlState {
    val inlineSql = formState.commonSql
    val sqlFile = formState.commonSqlFile?.trim().orEmpty()
    if (inlineSql.isNotBlank()) {
        return DefaultSqlState(mode = "INLINE", inlineText = inlineSql, catalogPath = "")
    }
    if (sqlFile.isBlank()) {
        return DefaultSqlState(mode = "NONE", inlineText = "", catalogPath = "")
    }
    return if (sqlResources.any { it.path == sqlFile }) {
        DefaultSqlState(mode = "CATALOG", inlineText = "", catalogPath = sqlFile)
    } else {
        DefaultSqlState(mode = "EXTERNAL", inlineText = "", catalogPath = "", externalRef = sqlFile)
    }
}

internal fun buildSourceSqlState(
    source: ConfigFormSourceStateDto,
    sqlResources: List<SqlResourceOption>,
): SourceSqlState {
    val inlineSql = source.sql.orEmpty()
    val sqlFile = source.sqlFile?.trim().orEmpty()
    if (inlineSql.isNotBlank()) {
        return SourceSqlState(
            mode = "INLINE",
            inlineText = inlineSql,
            catalogPath = "",
            summary = "Использует встроенный SQL.",
        )
    }
    if (sqlFile.isBlank()) {
        return SourceSqlState(
            mode = "INHERIT",
            inlineText = "",
            catalogPath = "",
            summary = "Наследует SQL по умолчанию.",
        )
    }
    return if (sqlResources.any { it.path == sqlFile }) {
        SourceSqlState(
            mode = "CATALOG",
            inlineText = "",
            catalogPath = sqlFile,
            summary = "Использует SQL-ресурс из каталога.",
        )
    } else {
        SourceSqlState(
            mode = "EXTERNAL",
            inlineText = "",
            catalogPath = "",
            externalRef = sqlFile,
            summary = "Использует внешнюю SQL-ссылку.",
        )
    }
}

internal fun buildDefaultSqlModeOptions(sqlState: DefaultSqlState): List<Pair<String, String>> =
    buildList {
        add("NONE" to "Не задан")
        add("INLINE" to "Встроенный SQL")
        add("CATALOG" to "SQL из каталога")
        if (sqlState.mode == "EXTERNAL") {
            add("EXTERNAL" to "Внешняя ссылка")
        }
    }

internal fun buildSourceSqlModeOptions(sqlState: SourceSqlState): List<Pair<String, String>> =
    buildList {
        add("INHERIT" to "Наследовать SQL по умолчанию")
        add("INLINE" to "Встроенный SQL")
        add("CATALOG" to "SQL из каталога")
        if (sqlState.mode == "EXTERNAL") {
            add("EXTERNAL" to "Внешняя ссылка")
        }
    }

internal fun applyDefaultSqlMode(
    formState: ConfigFormStateDto,
    mode: String,
    sqlResources: List<SqlResourceOption>,
): ConfigFormStateDto =
    when (mode) {
        "INLINE" -> formState.copy(commonSql = formState.commonSql, commonSqlFile = null)
        "CATALOG" -> formState.copy(
            commonSql = "",
            commonSqlFile = formState.commonSqlFile
                ?.takeIf { path -> sqlResources.any { it.path == path } }
                ?: sqlResources.firstOrNull()?.path,
        )
        "NONE" -> formState.copy(commonSql = "", commonSqlFile = null)
        else -> formState
    }

internal fun applySourceSqlMode(
    formState: ConfigFormStateDto,
    index: Int,
    mode: String,
    sqlResources: List<SqlResourceOption>,
): ConfigFormStateDto =
    updateSource(formState, index) {
        when (mode) {
            "INLINE" -> copy(sql = sql.orEmpty(), sqlFile = null)
            "CATALOG" -> copy(
                sql = null,
                sqlFile = sqlFile
                    ?.takeIf { path -> sqlResources.any { it.path == path } }
                    ?: sqlResources.firstOrNull()?.path,
            )
            "INHERIT" -> copy(sql = null, sqlFile = null)
            else -> this
        }
    }

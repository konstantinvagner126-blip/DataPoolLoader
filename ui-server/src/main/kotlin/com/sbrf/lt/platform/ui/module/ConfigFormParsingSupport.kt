package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ErrorMode
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse

internal class ConfigFormParsingSupport(
    private val mapper: ObjectMapper,
    private val defaults: AppConfig,
) {
    private val scalarSupport = ConfigFormScalarParsingSupport()
    private val collectionSupport = ConfigFormCollectionParsingSupport(defaults, scalarSupport)

    fun parse(configText: String): ConfigFormStateResponse {
        val warnings = mutableListOf<String>()
        val rootNode = mapper.readTree(configText)
        val appNode = (rootNode as? ObjectNode)?.path("app").takeIf { it != null && !it.isMissingNode }
        val appObject = appNode as? ObjectNode
        if (appNode != null && appNode !is ObjectNode) {
            warnings += "Раздел app имеет неподдерживаемую структуру. Использованы значения по умолчанию."
        }
        if (rootNode !is ObjectNode) {
            warnings += "Корень application.yml имеет неподдерживаемую структуру. Использованы значения по умолчанию."
        }
        val targetObject = appObject?.path("target").takeIf { it != null && !it.isMissingNode } as? ObjectNode
        if (appObject?.path("target") != null && !appObject.path("target").isMissingNode && appObject.path("target") !is ObjectNode) {
            warnings += "Раздел app.target имеет неподдерживаемую структуру. Использованы значения по умолчанию."
        }
        return ConfigFormStateResponse(
            outputDir = scalarSupport.readText(appObject, "outputDir", defaults.outputDir, warnings),
            fileFormat = scalarSupport.readText(appObject, "fileFormat", defaults.fileFormat, warnings),
            mergeMode = scalarSupport.readEnum(
                appObject,
                "mergeMode",
                defaults.mergeMode,
                MergeMode.entries,
                warnings,
            ).name.lowercase(),
            errorMode = scalarSupport.readEnum(
                appObject,
                "errorMode",
                defaults.errorMode,
                ErrorMode.entries,
                warnings,
            ).name.lowercase(),
            parallelism = scalarSupport.readInt(appObject, "parallelism", defaults.parallelism, warnings),
            fetchSize = scalarSupport.readInt(appObject, "fetchSize", defaults.fetchSize, warnings),
            queryTimeoutSec = scalarSupport.readOptionalInt(appObject, "queryTimeoutSec", defaults.queryTimeoutSec, warnings),
            progressLogEveryRows = scalarSupport.readLong(appObject, "progressLogEveryRows", defaults.progressLogEveryRows, warnings),
            maxMergedRows = scalarSupport.readOptionalLong(appObject, "maxMergedRows", defaults.maxMergedRows, warnings),
            deleteOutputFilesAfterCompletion = scalarSupport.readBoolean(
                appObject,
                "deleteOutputFilesAfterCompletion",
                defaults.deleteOutputFilesAfterCompletion,
                warnings,
            ),
            commonSql = scalarSupport.readFirstText(appObject, listOf("commonSql", "sql"), defaults.commonSql, warnings),
            commonSqlFile = scalarSupport.readOptionalText(appObject, "commonSqlFile", defaults.commonSqlFile, warnings),
            sources = collectionSupport.readSources(appObject, warnings),
            quotas = collectionSupport.readQuotas(appObject, warnings),
            targetEnabled = scalarSupport.readBoolean(targetObject, "enabled", defaults.target.enabled, warnings, "app.target.enabled"),
            targetJdbcUrl = scalarSupport.readText(targetObject, "jdbcUrl", defaults.target.jdbcUrl, warnings, "app.target.jdbcUrl"),
            targetUsername = scalarSupport.readText(targetObject, "username", defaults.target.username, warnings, "app.target.username"),
            targetPassword = scalarSupport.readText(targetObject, "password", defaults.target.password, warnings, "app.target.password"),
            targetTable = scalarSupport.readText(targetObject, "table", defaults.target.table, warnings, "app.target.table"),
            targetTruncateBeforeLoad = scalarSupport.readBoolean(
                targetObject,
                "truncateBeforeLoad",
                defaults.target.truncateBeforeLoad,
                warnings,
                "app.target.truncateBeforeLoad",
            ),
            warnings = warnings.distinct(),
        )
    }
}

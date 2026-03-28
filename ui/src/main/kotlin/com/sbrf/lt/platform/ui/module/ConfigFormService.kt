package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.model.ErrorMode
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.datapool.model.RootConfig
import com.sbrf.lt.platform.ui.model.ConfigFormQuotaState
import com.sbrf.lt.platform.ui.model.ConfigFormSourceState
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateResponse

class ConfigFormService(
    configLoader: ConfigLoader = ConfigLoader(),
) {
    private val mapper = configLoader.objectMapper()

    fun parse(configText: String): ConfigFormStateResponse {
        val appConfig = mapper.readValue(configText, RootConfig::class.java).app
        return ConfigFormStateResponse(
            outputDir = appConfig.outputDir,
            fileFormat = appConfig.fileFormat,
            mergeMode = appConfig.mergeMode.name.lowercase(),
            errorMode = appConfig.errorMode.name.lowercase(),
            parallelism = appConfig.parallelism,
            fetchSize = appConfig.fetchSize,
            queryTimeoutSec = appConfig.queryTimeoutSec,
            progressLogEveryRows = appConfig.progressLogEveryRows,
            maxMergedRows = appConfig.maxMergedRows,
            deleteOutputFilesAfterCompletion = appConfig.deleteOutputFilesAfterCompletion,
            commonSql = appConfig.commonSql,
            commonSqlFile = appConfig.commonSqlFile,
            sources = appConfig.sources.map {
                ConfigFormSourceState(
                    name = it.name,
                    jdbcUrl = it.jdbcUrl,
                    username = it.username,
                    password = it.password,
                    sql = it.sql,
                    sqlFile = it.sqlFile,
                )
            },
            quotas = appConfig.quotas.map {
                ConfigFormQuotaState(
                    source = it.source,
                    percent = it.percent,
                )
            },
            targetEnabled = appConfig.target.enabled,
            targetJdbcUrl = appConfig.target.jdbcUrl,
            targetUsername = appConfig.target.username,
            targetPassword = appConfig.target.password,
            targetTable = appConfig.target.table,
            targetTruncateBeforeLoad = appConfig.target.truncateBeforeLoad,
        )
    }

    fun apply(configText: String, formState: ConfigFormStateResponse): ConfigFormUpdateResponse {
        val rootNode = (mapper.readTree(configText) as? ObjectNode)
            ?: mapper.createObjectNode()
        val appNode = rootNode.withObject("/app")
        val targetNode = appNode.withObject("/target")

        appNode.put("outputDir", formState.outputDir)
        appNode.put("fileFormat", formState.fileFormat)
        appNode.put("mergeMode", normalizeMergeMode(formState.mergeMode).name.lowercase())
        appNode.put("errorMode", normalizeErrorMode(formState.errorMode).name.lowercase())
        appNode.put("parallelism", formState.parallelism)
        appNode.put("fetchSize", formState.fetchSize)
        if (formState.queryTimeoutSec == null) {
            appNode.remove("queryTimeoutSec")
        } else {
            appNode.put("queryTimeoutSec", formState.queryTimeoutSec)
        }
        appNode.put("progressLogEveryRows", formState.progressLogEveryRows)
        if (formState.maxMergedRows == null) {
            appNode.remove("maxMergedRows")
        } else {
            appNode.put("maxMergedRows", formState.maxMergedRows)
        }
        appNode.put("deleteOutputFilesAfterCompletion", formState.deleteOutputFilesAfterCompletion)
        setOptionalText(appNode, "commonSql", formState.commonSql)
        setOptionalText(appNode, "commonSqlFile", formState.commonSqlFile)

        setSources(appNode.putArray("sources"), formState.sources)
        setQuotas(appNode.putArray("quotas"), formState.quotas)

        targetNode.put("enabled", formState.targetEnabled)
        targetNode.put("jdbcUrl", formState.targetJdbcUrl)
        targetNode.put("username", formState.targetUsername)
        targetNode.put("password", formState.targetPassword)
        targetNode.put("table", formState.targetTable)
        targetNode.put("truncateBeforeLoad", formState.targetTruncateBeforeLoad)

        val updatedText = mapper.writeValueAsString(rootNode)
        return ConfigFormUpdateResponse(
            configText = updatedText,
            formState = parse(updatedText),
        )
    }

    private fun normalizeMergeMode(rawValue: String): MergeMode =
        MergeMode.entries.firstOrNull { it.name.equals(rawValue.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Неизвестный mergeMode: $rawValue")

    private fun normalizeErrorMode(rawValue: String): ErrorMode =
        ErrorMode.entries.firstOrNull { it.name.equals(rawValue.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Неизвестный errorMode: $rawValue")

    private fun setOptionalText(target: ObjectNode, fieldName: String, rawValue: String?) {
        val normalized = rawValue?.takeUnless { it.isBlank() }
        if (normalized == null) {
            target.remove(fieldName)
        } else {
            target.put(fieldName, normalized)
        }
    }

    private fun setSources(arrayNode: ArrayNode, sources: List<ConfigFormSourceState>) {
        sources.forEach { source ->
            arrayNode.addObject().apply {
                put("name", source.name)
                put("jdbcUrl", source.jdbcUrl)
                put("username", source.username)
                put("password", source.password)
                setOptionalText(this, "sql", source.sql)
                setOptionalText(this, "sqlFile", source.sqlFile)
            }
        }
    }

    private fun setQuotas(arrayNode: ArrayNode, quotas: List<ConfigFormQuotaState>) {
        quotas.forEach { quota ->
            arrayNode.addObject().apply {
                put("source", quota.source)
                if (quota.percent != null) {
                    put("percent", quota.percent)
                }
            }
        }
    }
}

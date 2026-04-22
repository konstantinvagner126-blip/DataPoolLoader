package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ErrorMode
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.platform.ui.model.ConfigFormQuotaState
import com.sbrf.lt.platform.ui.model.ConfigFormSourceState
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse

internal class ConfigFormParsingSupport(
    private val mapper: ObjectMapper,
    private val defaults: AppConfig,
) {
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
            outputDir = readText(appObject, "outputDir", defaults.outputDir, warnings),
            fileFormat = readText(appObject, "fileFormat", defaults.fileFormat, warnings),
            mergeMode = readEnum(
                appObject,
                "mergeMode",
                defaults.mergeMode,
                MergeMode.entries,
                warnings,
            ).name.lowercase(),
            errorMode = readEnum(
                appObject,
                "errorMode",
                defaults.errorMode,
                ErrorMode.entries,
                warnings,
            ).name.lowercase(),
            parallelism = readInt(appObject, "parallelism", defaults.parallelism, warnings),
            fetchSize = readInt(appObject, "fetchSize", defaults.fetchSize, warnings),
            queryTimeoutSec = readOptionalInt(appObject, "queryTimeoutSec", defaults.queryTimeoutSec, warnings),
            progressLogEveryRows = readLong(appObject, "progressLogEveryRows", defaults.progressLogEveryRows, warnings),
            maxMergedRows = readOptionalLong(appObject, "maxMergedRows", defaults.maxMergedRows, warnings),
            deleteOutputFilesAfterCompletion = readBoolean(
                appObject,
                "deleteOutputFilesAfterCompletion",
                defaults.deleteOutputFilesAfterCompletion,
                warnings,
            ),
            commonSql = readFirstText(appObject, listOf("commonSql", "sql"), defaults.commonSql, warnings),
            commonSqlFile = readOptionalText(appObject, "commonSqlFile", defaults.commonSqlFile, warnings),
            sources = readSources(appObject, warnings),
            quotas = readQuotas(appObject, warnings),
            targetEnabled = readBoolean(targetObject, "enabled", defaults.target.enabled, warnings, "app.target.enabled"),
            targetJdbcUrl = readText(targetObject, "jdbcUrl", defaults.target.jdbcUrl, warnings, "app.target.jdbcUrl"),
            targetUsername = readText(targetObject, "username", defaults.target.username, warnings, "app.target.username"),
            targetPassword = readText(targetObject, "password", defaults.target.password, warnings, "app.target.password"),
            targetTable = readText(targetObject, "table", defaults.target.table, warnings, "app.target.table"),
            targetTruncateBeforeLoad = readBoolean(
                targetObject,
                "truncateBeforeLoad",
                defaults.target.truncateBeforeLoad,
                warnings,
                "app.target.truncateBeforeLoad",
            ),
            warnings = warnings.distinct(),
        )
    }

    private fun readSources(
        appNode: ObjectNode?,
        warnings: MutableList<String>,
    ): List<ConfigFormSourceState> {
        val sourcesNode = appNode?.path("sources")
        if (sourcesNode == null || sourcesNode.isMissingNode || sourcesNode.isNull) {
            return defaults.sources.map {
                ConfigFormSourceState(
                    name = it.name,
                    jdbcUrl = it.jdbcUrl,
                    username = it.username,
                    password = it.password,
                    sql = it.sql,
                    sqlFile = it.sqlFile,
                )
            }
        }
        if (sourcesNode !is ArrayNode) {
            warnings += "Раздел app.sources имеет неподдерживаемую структуру. Источники не загружены в визуальную форму."
            return emptyList()
        }
        return sourcesNode.mapIndexedNotNull { index, item ->
            val sourceNode = item as? ObjectNode
            if (sourceNode == null) {
                warnings += "Источник app.sources[$index] имеет неподдерживаемую структуру и пропущен."
                return@mapIndexedNotNull null
            }
            ConfigFormSourceState(
                name = readText(sourceNode, "name", "", warnings, "app.sources[$index].name"),
                jdbcUrl = readText(sourceNode, "jdbcUrl", "", warnings, "app.sources[$index].jdbcUrl"),
                username = readText(sourceNode, "username", "", warnings, "app.sources[$index].username"),
                password = readText(sourceNode, "password", "", warnings, "app.sources[$index].password"),
                sql = readOptionalText(sourceNode, "sql", null, warnings, "app.sources[$index].sql"),
                sqlFile = readOptionalText(sourceNode, "sqlFile", null, warnings, "app.sources[$index].sqlFile"),
            )
        }
    }

    private fun readQuotas(
        appNode: ObjectNode?,
        warnings: MutableList<String>,
    ): List<ConfigFormQuotaState> {
        val quotasNode = appNode?.path("quotas")
        if (quotasNode == null || quotasNode.isMissingNode || quotasNode.isNull) {
            return defaults.quotas.map {
                ConfigFormQuotaState(
                    source = it.source,
                    percent = it.percent,
                )
            }
        }
        if (quotasNode !is ArrayNode) {
            warnings += "Раздел app.quotas имеет неподдерживаемую структуру. Квоты не загружены в визуальную форму."
            return emptyList()
        }
        return quotasNode.mapIndexedNotNull { index, item ->
            val quotaNode = item as? ObjectNode
            if (quotaNode == null) {
                warnings += "Квота app.quotas[$index] имеет неподдерживаемую структуру и пропущена."
                return@mapIndexedNotNull null
            }
            ConfigFormQuotaState(
                source = readText(quotaNode, "source", "", warnings, "app.quotas[$index].source"),
                percent = readOptionalDouble(quotaNode, "percent", null, warnings, "app.quotas[$index].percent"),
            )
        }
    }

    private fun readText(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: String,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): String {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return if (node.isValueNode) {
            node.asText()
        } else {
            warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
            defaultValue
        }
    }

    private fun readFirstText(
        parent: ObjectNode?,
        fieldNames: List<String>,
        defaultValue: String,
        warnings: MutableList<String>,
    ): String {
        fieldNames.forEach { fieldName ->
            val path = "app.$fieldName"
            val node = parent?.path(fieldName)
            if (node != null && !node.isMissingNode && !node.isNull) {
                return readText(parent, fieldName, defaultValue, warnings, path)
            }
        }
        return defaultValue
    }

    private fun readOptionalText(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: String?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): String? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return if (node.isValueNode) {
            node.asText().takeUnless { it.isBlank() }
        } else {
            warnings += "Поле $path имеет неподдерживаемый тип. Значение пропущено."
            defaultValue
        }
    }

    private fun readInt(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Int,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Int {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseIntNode(node, path, warnings) ?: defaultValue
    }

    private fun readOptionalInt(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Int?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Int? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseIntNode(node, path, warnings) ?: defaultValue
    }

    private fun readLong(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Long,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Long {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseLongNode(node, path, warnings) ?: defaultValue
    }

    private fun readOptionalLong(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Long?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Long? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseLongNode(node, path, warnings) ?: defaultValue
    }

    private fun readOptionalDouble(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Double?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Double? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseDoubleNode(node, path, warnings) ?: defaultValue
    }

    private fun readBoolean(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Boolean,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Boolean {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseBooleanNode(node, path, warnings) ?: defaultValue
    }

    private fun <T : Enum<T>> readEnum(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: T,
        entries: List<T>,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): T {
        val rawValue = readOptionalText(parent, fieldName, null, warnings, path) ?: return defaultValue
        return entries.firstOrNull { it.name.equals(rawValue.trim(), ignoreCase = true) }
            ?: defaultValue.also {
                warnings += "Поле $path содержит неизвестное значение '$rawValue'. Использовано значение по умолчанию."
            }
    }

    private fun parseIntNode(
        node: JsonNode,
        path: String,
        warnings: MutableList<String>,
    ): Int? {
        if (node.isInt || node.isLong) {
            return node.asInt()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toIntOrNull()
                ?: run {
                    warnings += "Поле $path должно быть числом. Использовано значение по умолчанию."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }

    private fun parseLongNode(
        node: JsonNode,
        path: String,
        warnings: MutableList<String>,
    ): Long? {
        if (node.isIntegralNumber) {
            return node.asLong()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toLongOrNull()
                ?: run {
                    warnings += "Поле $path должно быть целым числом. Использовано значение по умолчанию."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }

    private fun parseDoubleNode(
        node: JsonNode,
        path: String,
        warnings: MutableList<String>,
    ): Double? {
        if (node.isNumber) {
            return node.asDouble()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toDoubleOrNull()
                ?: run {
                    warnings += "Поле $path должно быть числом. Значение пропущено."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Значение пропущено."
        return null
    }

    private fun parseBooleanNode(
        node: JsonNode,
        path: String,
        warnings: MutableList<String>,
    ): Boolean? {
        if (node.isBoolean) {
            return node.booleanValue()
        }
        if (node.isTextual) {
            return when (node.asText().trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> {
                    warnings += "Поле $path должно быть true/false. Использовано значение по умолчанию."
                    null
                }
            }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }
}

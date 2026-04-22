package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.platform.ui.model.ConfigFormQuotaState
import com.sbrf.lt.platform.ui.model.ConfigFormSourceState

internal class ConfigFormCollectionParsingSupport(
    private val defaults: AppConfig,
    private val scalarSupport: ConfigFormScalarParsingSupport,
) {
    fun readSources(
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
                name = scalarSupport.readText(sourceNode, "name", "", warnings, "app.sources[$index].name"),
                jdbcUrl = scalarSupport.readText(sourceNode, "jdbcUrl", "", warnings, "app.sources[$index].jdbcUrl"),
                username = scalarSupport.readText(sourceNode, "username", "", warnings, "app.sources[$index].username"),
                password = scalarSupport.readText(sourceNode, "password", "", warnings, "app.sources[$index].password"),
                sql = scalarSupport.readOptionalText(sourceNode, "sql", null, warnings, "app.sources[$index].sql"),
                sqlFile = scalarSupport.readOptionalText(sourceNode, "sqlFile", null, warnings, "app.sources[$index].sqlFile"),
            )
        }
    }

    fun readQuotas(
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
                source = scalarSupport.readText(quotaNode, "source", "", warnings, "app.quotas[$index].source"),
                percent = scalarSupport.readOptionalDouble(quotaNode, "percent", null, warnings, "app.quotas[$index].percent"),
            )
        }
    }
}

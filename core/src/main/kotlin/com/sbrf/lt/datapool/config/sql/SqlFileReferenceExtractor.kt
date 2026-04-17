package com.sbrf.lt.datapool.config.sql

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Общая логика извлечения и нормализации SQL-ссылок из YAML-конфига.
 */
object SqlFileReferenceExtractor {

    fun extractOrEmpty(configText: String, mapper: ObjectMapper): List<SqlFileReference> =
        try {
            extract(configText, mapper)
        } catch (_: JsonProcessingException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    fun extract(configText: String, mapper: ObjectMapper): List<SqlFileReference> {
        if (configText.isBlank()) {
            return emptyList()
        }
        val root = mapper.readTree(configText) ?: return emptyList()
        val app = root.path("app")
        val refs = linkedMapOf<String, SqlFileReference>()
        app.path("commonSqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
            refs[path] = SqlFileReference(label = "Общий SQL", path = path)
        }
        app.path("sources").takeIf { it.isArray }?.forEach { source ->
            val sourceName = source.path("name").takeIf { it.isTextual }?.asText()?.ifBlank { "source" } ?: "source"
            source.path("sqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
                refs[path] = SqlFileReference(label = "Источник: $sourceName", path = path)
            }
        }
        return refs.values.toList()
    }

    fun labelsByPathOrEmpty(configText: String, mapper: ObjectMapper): Map<String, String> =
        extractOrEmpty(configText, mapper).associate { it.path to it.label }
}

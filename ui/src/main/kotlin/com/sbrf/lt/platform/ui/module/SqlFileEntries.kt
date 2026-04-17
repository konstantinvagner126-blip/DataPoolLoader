package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ModuleFileContent

/**
 * Общая логика извлечения и нормализации SQL-ссылок из YAML-конфига UI-модуля.
 */
internal object SqlFileEntries {

    fun extractOrEmpty(configText: String, mapper: ObjectMapper): List<SqlFileEntry> =
        try {
            extract(configText, mapper)
        } catch (_: JsonProcessingException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    fun extract(configText: String, mapper: ObjectMapper): List<SqlFileEntry> {
        if (configText.isBlank()) {
            return emptyList()
        }
        val root = mapper.readTree(configText) ?: return emptyList()
        val app = root.path("app")
        val refs = linkedMapOf<String, SqlFileEntry>()
        app.path("commonSqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
            refs[path] = SqlFileEntry(label = "Общий SQL", path = path)
        }
        app.path("sources").takeIf { it.isArray }?.forEach { source ->
            val sourceName = source.path("name").takeIf { it.isTextual }?.asText()?.ifBlank { "source" } ?: "source"
            source.path("sqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
                refs[path] = SqlFileEntry(label = "Источник: $sourceName", path = path)
            }
        }
        return refs.values.toList()
    }

    fun labelsByPathOrEmpty(configText: String, mapper: ObjectMapper): Map<String, String> =
        extractOrEmpty(configText, mapper).associate { it.path to it.label }

    fun relabel(configText: String, sqlFiles: List<ModuleFileContent>, mapper: ObjectMapper): List<ModuleFileContent> {
        if (sqlFiles.isEmpty()) {
            return sqlFiles
        }
        val labelsByPath = labelsByPathOrEmpty(configText, mapper)
        return sqlFiles.map { file ->
            val expectedLabel = labelsByPath[file.path]
            if (expectedLabel != null && (file.label.isBlank() || file.label == file.path)) {
                file.copy(label = expectedLabel)
            } else {
                file
            }
        }
    }
}

package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import java.security.MessageDigest

/**
 * Единый контракт сериализации working/execution snapshot и расчета content hash
 * для DB-модулей.
 */
class DatabaseModuleSnapshotSupport(
    private val objectMapper: ObjectMapper,
) {
    fun serializeWorkingCopySnapshot(
        configText: String,
        sqlFileContents: Map<String, String>,
        title: String,
        description: String?,
        tags: List<String>,
        hiddenFromUi: Boolean,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("configText", configText)
        root.put("title", title)
        root.put("description", description)
        root.set<JsonNode>("tags", objectMapper.valueToTree(tags))
        root.put("hiddenFromUi", hiddenFromUi)
        root.set<JsonNode>("sqlFiles", objectMapper.valueToTree(labelSqlFiles(configText, sqlFileContents)))
        return objectMapper.writeValueAsString(root)
    }

    fun serializeExecutionSnapshot(
        configText: String,
        sqlFileContents: Map<String, String>,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("configText", configText)
        root.set<JsonNode>("sqlFiles", objectMapper.valueToTree(labelSqlFiles(configText, sqlFileContents)))
        return objectMapper.writeValueAsString(root)
    }

    fun calculateRevisionContentHash(
        configText: String,
        title: String,
        description: String?,
        tags: List<String>,
        hiddenFromUi: Boolean,
        sqlFiles: Map<String, String>,
    ): String = sha256Hex(
        buildString {
            append(configText)
            append('\u0000')
            append(title)
            append('\u0000')
            append(description.orEmpty())
            append('\u0000')
            append(tags.joinToString("|"))
            append('\u0000')
            append(hiddenFromUi)
            appendSqlFiles(sqlFiles)
        },
    )

    fun calculateExecutionContentHash(
        configText: String,
        sqlFiles: Map<String, String>,
    ): String = sha256Hex(
        buildString {
            append(configText)
            appendSqlFiles(sqlFiles)
        },
    )

    fun deserializeWorkingCopySnapshot(workingCopyJson: String): WorkingCopySnapshot =
        objectMapper.readValue(workingCopyJson, WorkingCopySnapshot::class.java)

    fun deserializeWorkingCopySqlFiles(workingCopyJson: String): List<ModuleFileContent> {
        val snapshot = deserializeWorkingCopySnapshot(workingCopyJson)
        if (snapshot.sqlFiles.isEmpty()) {
            return emptyList()
        }
        val labelsByPath = SqlFileReferenceExtractor.labelsByPathOrEmpty(snapshot.configText, objectMapper)
        return snapshot.sqlFiles.map { file ->
            val expectedLabel = labelsByPath[file.path]
            if (expectedLabel != null && (file.label.isBlank() || file.label == file.path)) {
                file.copy(label = expectedLabel)
            } else {
                file
            }
        }
    }

    fun deserializeWorkingCopySqlFileContents(workingCopyJson: String?): Map<String, String> {
        if (workingCopyJson.isNullOrBlank()) {
            return emptyMap()
        }
        return deserializeWorkingCopySqlFiles(workingCopyJson).associate { it.path to it.content }
    }

    private fun labelSqlFiles(
        configText: String,
        sqlFileContents: Map<String, String>,
    ): List<ModuleFileContent> {
        val sqlLabels = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
        return sqlFileContents.entries
            .sortedBy { it.key }
            .map { (path, content) ->
                ModuleFileContent(
                    label = sqlLabels[path] ?: path,
                    path = path,
                    content = content,
                    exists = true,
                )
            }
    }

    private fun StringBuilder.appendSqlFiles(sqlFiles: Map<String, String>) {
        sqlFiles.toSortedMap().forEach { (path, content) ->
            append('\n')
            append(path)
            append('\u0000')
            append(content)
        }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class ModuleRegistryMetadataSupport(
    private val mapper: ObjectMapper,
) {
    fun loadMetadata(metadataFile: java.nio.file.Path): ModuleMetadataResult {
        if (!metadataFile.exists()) {
            return ModuleMetadataResult()
        }
        return try {
            val root = mapper.readTree(metadataFile.readText())
            ModuleMetadataResult(
                title = root.path("title").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() },
                description = root.path("description").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() },
                tags = root.path("tags")
                    .takeIf { it.isArray }
                    ?.mapNotNull { node -> node.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() } }
                    .orEmpty(),
                hiddenFromUi = root.path("hiddenFromUi").takeIf { it.isBoolean }?.asBoolean() ?: false,
            )
        } catch (error: Exception) {
            ModuleMetadataResult(
                issue = ModuleValidationIssueResponse(
                    severity = "WARNING",
                    message = "ui-module.yml не удалось разобрать: ${error.message ?: "ошибка синтаксиса YAML"}",
                ),
            )
        }
    }

    fun writeMetadata(module: ModuleDescriptor, request: SaveModuleRequest) {
        val metadataFile = module.configFile.parent.parent.parent.parent.resolve("ui-module.yml")
        val normalizedTitle = request.title.trim()
        val normalizedDescription = request.description?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedTags = request.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val normalizedHidden = request.hiddenFromUi
        if (normalizedTitle == module.id && normalizedDescription == null && normalizedTags.isEmpty() && !normalizedHidden) {
            metadataFile.deleteIfExists()
            return
        }
        val root = linkedMapOf<String, Any>(
            "title" to normalizedTitle,
        )
        normalizedDescription?.let { root["description"] = it }
        if (normalizedTags.isNotEmpty()) {
            root["tags"] = normalizedTags
        }
        if (normalizedHidden) {
            root["hiddenFromUi"] = true
        }
        metadataFile.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
    }
}

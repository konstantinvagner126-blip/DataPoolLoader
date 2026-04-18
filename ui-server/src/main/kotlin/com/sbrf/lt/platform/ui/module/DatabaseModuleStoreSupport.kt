package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStatusValue
import java.security.MessageDigest
import java.sql.ResultSet

internal class DatabaseModuleStoreSupport(
    private val objectMapper: ObjectMapper,
    private val validationService: ModuleValidationService,
) {
    private val tagsType = object : TypeReference<List<String>>() {}
    private val issuesType = object : TypeReference<List<ModuleValidationIssueResponse>>() {}

    fun catalogItem(
        resultSet: ResultSet,
        configText: String,
        sqlFiles: List<ModuleFileContent>,
    ): ModuleCatalogItemResponse {
        val validation = validateModule(configText, sqlFiles)
        return ModuleCatalogItemResponse(
            id = resultSet.getString("module_code"),
            descriptor = ModuleMetadataDescriptorResponse(
                title = resultSet.getString("title"),
                description = resultSet.getString("description"),
                tags = readJsonList(resultSet.getString("tags_json"), tagsType),
                hiddenFromUi = resultSet.getBoolean("hidden_from_ui"),
            ),
            validationStatus = validation.toStatusValue(),
            validationIssues = validation.toResponse(),
        )
    }

    fun moduleDetails(
        row: DatabaseEditableModuleRow,
        sqlFiles: List<ModuleFileContent>,
    ): ModuleDetailsResponse {
        val workingCopySnapshot = row.workingCopyJson?.let(::readWorkingCopySnapshot)
        val validation = validateModule(row.configText, sqlFiles)
        return ModuleDetailsResponse(
            id = row.moduleCode,
            descriptor = ModuleMetadataDescriptorResponse(
                title = workingCopySnapshot?.title ?: row.title,
                description = workingCopySnapshot?.description ?: row.description,
                tags = workingCopySnapshot?.tags ?: row.tags,
                hiddenFromUi = workingCopySnapshot?.hiddenFromUi ?: row.hiddenFromUi,
            ),
            validationStatus = validation.toStatusValue(),
            validationIssues = validation.toResponse(),
            configPath = "db:${row.moduleCode}",
            configText = row.configText,
            sqlFiles = sqlFiles,
            requiresCredentials = false,
            credentialsStatus = CredentialsStatusResponse(
                mode = "NONE",
                displayName = "Файл не задан",
                fileAvailable = false,
                uploaded = false,
            ),
            requiredCredentialKeys = emptyList(),
            missingCredentialKeys = emptyList(),
            credentialsReady = true,
        )
    }

    fun buildWorkingCopyJson(request: SaveModuleRequest): String =
        buildSnapshotJson(
            configText = request.configText,
            sqlFileContents = request.sqlFiles,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
        )

    fun buildSnapshotJson(
        configText: String,
        sqlFileContents: Map<String, String>,
        title: String,
        description: String?,
        tags: List<String>,
        hiddenFromUi: Boolean,
    ): String {
        val sqlLabels = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
        val sqlFiles = sqlFileContents.entries
            .sortedBy { it.key }
            .map { (path, content) ->
                ModuleFileContent(
                    label = sqlLabels[path] ?: path,
                    path = path,
                    content = content,
                    exists = true,
                )
            }
        val root = objectMapper.createObjectNode()
        root.put("configText", configText)
        root.put("title", title)
        root.put("description", description)
        root.putPOJO("tags", tags)
        root.put("hiddenFromUi", hiddenFromUi)
        root.set<com.fasterxml.jackson.databind.JsonNode>("sqlFiles", objectMapper.valueToTree(sqlFiles))
        return objectMapper.writeValueAsString(root)
    }

    fun contentHash(request: SaveModuleRequest): String {
        val input = buildString {
            append(request.configText)
            append('\u0000')
            append(request.title)
            append('\u0000')
            append(request.description.orEmpty())
            append('\u0000')
            append(request.tags.joinToString("|"))
            append('\u0000')
            append(request.hiddenFromUi)
            request.sqlFiles.toSortedMap().forEach { (path, content) ->
                append('\n')
                append(path)
                append('\u0000')
                append(content)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun readWorkingCopySqlFiles(workingCopyJson: String): List<ModuleFileContent> {
        val snapshot = readWorkingCopySnapshot(workingCopyJson)
        return relabelSqlFiles(snapshot.configText, snapshot.sqlFiles)
    }

    fun readSqlFileContents(workingCopyJson: String?): Map<String, String> {
        if (workingCopyJson.isNullOrBlank()) return emptyMap()
        return readWorkingCopySqlFiles(workingCopyJson).associate { it.path to it.content }
    }

    fun editableModuleRow(resultSet: ResultSet): DatabaseEditableModuleRow =
        DatabaseEditableModuleRow(
            moduleCode = resultSet.getString("module_code"),
            title = resultSet.getString("title"),
            description = resultSet.getString("description"),
            tags = readJsonList(resultSet.getString("tags_json"), tagsType),
            hiddenFromUi = resultSet.getBoolean("hidden_from_ui"),
            validationStatus = resultSet.getString("validation_status"),
            validationIssues = readJsonList(resultSet.getString("validation_issues_json"), issuesType),
            configText = resultSet.getString("config_text"),
            sourceKind = resultSet.getString("source_kind"),
            currentRevisionId = resultSet.getString("current_revision_id"),
            workingCopyId = resultSet.getString("working_copy_id"),
            workingCopyStatus = resultSet.getString("working_copy_status"),
            baseRevisionId = resultSet.getString("base_revision_id"),
            workingCopyJson = resultSet.getString("working_copy_json"),
        )

    private fun <T> readJsonList(json: String?, type: TypeReference<List<T>>): List<T> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return objectMapper.readValue(json, type)
    }

    private fun relabelSqlFiles(configText: String, sqlFiles: List<ModuleFileContent>): List<ModuleFileContent> {
        if (sqlFiles.isEmpty()) {
            return sqlFiles
        }
        val labelsByPath = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
        return sqlFiles.map { file ->
            val expectedLabel = labelsByPath[file.path]
            if (expectedLabel != null && (file.label.isBlank() || file.label == file.path)) {
                file.copy(label = expectedLabel)
            } else {
                file
            }
        }
    }

    private fun readWorkingCopySnapshot(workingCopyJson: String): WorkingCopySnapshot =
        objectMapper.readValue(workingCopyJson, WorkingCopySnapshot::class.java)

    private fun validateModule(
        configText: String,
        sqlFiles: List<ModuleFileContent>,
    ) = validationService.validate(
        configText = configText,
        sqlReferenceExists = { entry -> sqlFiles.any { it.path == entry.path } },
    )
}

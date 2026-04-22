package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStatusValue
import java.sql.ResultSet

/**
 * UI-представление DB-модулей поверх registry-строк и уже собранного snapshot содержимого.
 */
internal class DatabaseModuleStorePresentationSupport(
    private val objectMapper: ObjectMapper,
    private val validationService: ModuleValidationService,
    private val snapshotSupport: DatabaseModuleSnapshotSupport,
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
        val workingCopySnapshot = row.workingCopyJson?.let(snapshotSupport::deserializeWorkingCopySnapshot)
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

    private fun validateModule(
        configText: String,
        sqlFiles: List<ModuleFileContent>,
    ) = validationService.validate(
        configText = configText,
        sqlReferenceExists = { entry -> sqlFiles.any { it.path == entry.path } },
    )
}

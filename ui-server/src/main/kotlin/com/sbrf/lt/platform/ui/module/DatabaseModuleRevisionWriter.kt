package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.RootConfig
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStatusValue
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Компонент записи revision-данных DB-модуля: revision, SQL assets, sources, target и quotas.
 */
internal class DatabaseModuleRevisionWriter(
    private val objectMapper: ObjectMapper,
    private val validationService: ModuleValidationService = ModuleValidationService(),
    private val snapshotSupport: DatabaseModuleSnapshotSupport = DatabaseModuleSnapshotSupport(objectMapper),
    private val sqlAssetSupport: DatabaseModuleRevisionSqlAssetSupport = DatabaseModuleRevisionSqlAssetSupport(),
    private val structureSupport: DatabaseModuleRevisionStructureSupport = DatabaseModuleRevisionStructureSupport(),
) {
    fun insertPublishedRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        currentRevisionId: String,
        revisionNo: Long,
        actorId: String,
        actorDisplayName: String?,
        workingCopyJson: String,
        workingCopyYaml: String,
        contentHash: String,
    ) {
        val appConfig = parseAppConfig(workingCopyYaml)
        val snapshot = snapshotSupport.deserializeWorkingCopySnapshot(workingCopyJson)
        val validation = validationService.validate(
            configText = workingCopyYaml,
            sqlReferenceExists = { entry -> snapshot.sqlFiles.any { it.path == entry.path } },
        )
        connection.prepareStatement(ModuleRegistrySql.insertPublishedRevision(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, revisionNo)
            stmt.setString(4, actorDisplayName ?: actorId)
            stmt.setString(5, snapshot.title ?: "")
            stmt.setString(6, snapshot.description)
            stmt.setString(7, objectMapper.writeValueAsString(snapshot.tags))
            stmt.setBoolean(8, snapshot.hiddenFromUi)
            stmt.setString(9, validation.toStatusValue())
            stmt.setString(10, objectMapper.writeValueAsString(validation.toResponse()))
            stmt.setString(11, appConfig.outputDir)
            stmt.setString(12, appConfig.fileFormat.uppercase())
            stmt.setString(13, appConfig.mergeMode.name)
            stmt.setString(14, appConfig.errorMode.name)
            stmt.setInt(15, appConfig.parallelism)
            stmt.setInt(16, appConfig.fetchSize)
            setNullableInt(stmt, 17, appConfig.queryTimeoutSec)
            stmt.setLong(18, appConfig.progressLogEveryRows)
            setNullableLong(stmt, 19, appConfig.maxMergedRows)
            stmt.setBoolean(20, appConfig.deleteOutputFilesAfterCompletion)
            stmt.setString(21, workingCopyJson)
            stmt.setString(22, workingCopyYaml)
            stmt.setString(23, contentHash)
            check(stmt.executeUpdate() == 1) {
                "Не удалось создать published revision для module_id=$moduleId current_revision_id=$currentRevisionId"
            }
        }
    }

    fun insertInitialRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        revisionSource: String,
        request: RegistryModuleDraft,
        actorId: String,
    ) {
        val tagsArray = objectMapper.createArrayNode()
        request.tags.forEach { tagsArray.add(it) }

        val appConfig = parseAppConfig(request.configText)
        val snapshotJson = snapshotSupport.serializeWorkingCopySnapshot(
            configText = request.configText,
            sqlFileContents = request.sqlFiles,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
        )
        val validation = validationService.validate(
            configText = request.configText,
            sqlReferenceExists = { entry -> request.sqlFiles.containsKey(entry.path) },
        )

        connection.prepareStatement(ModuleRegistrySql.insertRevision(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, 1)
            stmt.setString(4, actorId)
            stmt.setString(5, revisionSource)
            stmt.setString(6, request.title)
            stmt.setString(7, request.description)
            stmt.setString(8, tagsArray.toString())
            stmt.setBoolean(9, request.hiddenFromUi)
            stmt.setString(10, validation.toStatusValue())
            stmt.setString(11, objectMapper.writeValueAsString(validation.toResponse()))
            stmt.setString(12, appConfig.outputDir)
            stmt.setString(13, appConfig.fileFormat.uppercase())
            stmt.setString(14, appConfig.mergeMode.name)
            stmt.setString(15, appConfig.errorMode.name)
            stmt.setInt(16, appConfig.parallelism)
            stmt.setInt(17, appConfig.fetchSize)
            setNullableInt(stmt, 18, appConfig.queryTimeoutSec)
            stmt.setLong(19, appConfig.progressLogEveryRows)
            setNullableLong(stmt, 20, appConfig.maxMergedRows)
            stmt.setBoolean(21, appConfig.deleteOutputFilesAfterCompletion)
            stmt.setString(22, snapshotJson)
            stmt.setString(23, request.configText)
            stmt.setString(
                24,
                snapshotSupport.calculateRevisionContentHash(
                    configText = request.configText,
                    title = request.title,
                    description = request.description,
                    tags = request.tags,
                    hiddenFromUi = request.hiddenFromUi,
                    sqlFiles = request.sqlFiles,
                ),
            )
            stmt.executeUpdate()
        }
    }

    fun insertRevisionSqlAssets(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        configText: String,
        sqlFiles: Map<String, String>,
    ): Map<String, String> {
        val appConfig = parseAppConfig(configText)
        return sqlAssetSupport.insertRevisionSqlAssets(connection, normalizedSchema, revisionId, appConfig, sqlFiles)
    }

    fun insertRevisionStructure(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        configText: String,
        sqlAssetIds: Map<String, String>,
    ) {
        val appConfig = parseAppConfig(configText)
        structureSupport.insertRevisionStructure(connection, normalizedSchema, revisionId, appConfig, sqlAssetIds)
    }

    fun contentHashForCreate(request: RegistryModuleDraft): String {
        return snapshotSupport.calculateRevisionContentHash(
            configText = request.configText,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
            sqlFiles = request.sqlFiles,
        )
    }

    private fun parseAppConfig(configText: String): AppConfig =
        try {
            ConfigLoader().objectMapper().readValue(configText, RootConfig::class.java).app
        } catch (_: Exception) {
            AppConfig()
        }

    private fun setNullableInt(statement: PreparedStatement, index: Int, value: Int?) {
        if (value == null) {
            statement.setNull(index, Types.INTEGER)
        } else {
            statement.setInt(index, value)
        }
    }

    private fun setNullableLong(statement: PreparedStatement, index: Int, value: Long?) {
        if (value == null) {
            statement.setNull(index, Types.BIGINT)
        } else {
            statement.setLong(index, value)
        }
    }
}

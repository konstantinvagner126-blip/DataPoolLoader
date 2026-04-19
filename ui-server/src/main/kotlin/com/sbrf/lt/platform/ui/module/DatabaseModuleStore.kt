package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection
import java.util.UUID

open class DatabaseModuleStore(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val validationService: ModuleValidationService = ModuleValidationService(),
) : DatabaseModuleRegistryOperations {
    private val revisionWriter = DatabaseModuleRevisionWriter(objectMapper, validationService)
    private val support = DatabaseModuleStoreSupport(objectMapper, validationService)
    private val lifecycleSupport = DatabaseModuleStoreLifecycleSupport(support, revisionWriter)
    private val querySupport = DatabaseModuleStoreQuerySupport(connectionProvider, schema, support)

    override fun listModules(includeHidden: Boolean) = querySupport.listModules(includeHidden)

    override fun loadModuleDetails(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseEditableModule = querySupport.loadModuleDetails(moduleCode, actorId, actorSource)

    override fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val module = loadModuleForSave(connection, normalizedSchema, moduleCode, actorId, actorSource)
                lifecycleSupport.upsertWorkingCopy(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    module = module,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    request = request,
                )
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    override fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(ModuleRegistrySql.discardWorkingCopy(normalizedSchema)).use { statement ->
                statement.setString(1, actorId)
                statement.setString(2, actorSource)
                statement.setString(3, moduleCode)
                statement.executeUpdate()
            }
        }
    }

    override fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleInfo = lifecycleSupport.loadModuleForPublish(
                    connection,
                    normalizedSchema,
                    moduleCode,
                    actorId,
                    actorSource,
                )
                require(moduleInfo.hasWorkingCopy) {
                    "Нет личного черновика для публикации. Сначала сохраните изменения."
                }
                require(moduleInfo.baseRevisionId == moduleInfo.currentRevisionId && moduleInfo.workingCopyStatus != "STALE") {
                    "Личный черновик устарел относительно текущей ревизии. Перезагрузите модуль и примените изменения заново."
                }

                val newRevisionId = UUID.randomUUID().toString()
                val newRevisionNo = moduleInfo.maxRevisionNo + 1

                revisionWriter.insertPublishedRevision(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    moduleId = moduleInfo.moduleId,
                    currentRevisionId = moduleInfo.currentRevisionId,
                    revisionNo = newRevisionNo,
                    actorId = actorId,
                    actorDisplayName = actorDisplayName,
                    workingCopyJson = moduleInfo.workingCopyJson!!,
                    workingCopyYaml = moduleInfo.workingCopyYaml!!,
                    contentHash = moduleInfo.contentHash!!,
                )

                val sqlAssetIds = revisionWriter.insertRevisionSqlAssets(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    configText = moduleInfo.workingCopyYaml,
                    sqlFiles = querySupport.readSqlFileContents(moduleInfo.workingCopyJson),
                )
                revisionWriter.insertRevisionStructure(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    configText = moduleInfo.workingCopyYaml,
                    sqlAssetIds = sqlAssetIds,
                )

                lifecycleSupport.updateModuleCurrentRevision(connection, normalizedSchema, moduleInfo.moduleId, newRevisionId)

                lifecycleSupport.deleteWorkingCopyAfterPublish(
                    connection,
                    normalizedSchema,
                    moduleInfo.moduleId,
                    actorId,
                    actorSource,
                )

                connection.commit()
                return PublishResult(
                    revisionId = newRevisionId,
                    revisionNo = newRevisionNo,
                    moduleCode = moduleCode,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    override fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        request: CreateModuleRequest,
    ): CreateModuleResult {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleId = UUID.randomUUID().toString()
                val revisionId = UUID.randomUUID().toString()
                val workingCopyId = UUID.randomUUID().toString()

                lifecycleSupport.insertNewModule(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    moduleId = moduleId,
                    moduleCode = moduleCode,
                    originKind = originKind,
                )

                val revisionSource = if (originKind == "IMPORTED_FROM_FILES") "SYNC_FROM_FILES" else "CREATE_MODULE"
                revisionWriter.insertInitialRevision(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = revisionId,
                    moduleId = moduleId,
                    revisionSource = revisionSource,
                    request = request,
                    actorId = actorId,
                )
                val sqlAssetIds = revisionWriter.insertRevisionSqlAssets(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = revisionId,
                    configText = request.configText,
                    sqlFiles = request.sqlFiles,
                )
                revisionWriter.insertRevisionStructure(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = revisionId,
                    configText = request.configText,
                    sqlAssetIds = sqlAssetIds,
                )

                lifecycleSupport.updateModuleCurrentRevision(connection, normalizedSchema, moduleId, revisionId)

                lifecycleSupport.insertInitialWorkingCopy(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    workingCopyId = workingCopyId,
                    moduleId = moduleId,
                    revisionId = revisionId,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    request = request,
                )

                connection.commit()
                return CreateModuleResult(
                    moduleId = moduleId,
                    moduleCode = moduleCode,
                    revisionId = revisionId,
                    workingCopyId = workingCopyId,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    override fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleInfo = lifecycleSupport.loadModuleForDelete(connection, normalizedSchema, moduleCode)

                require(!moduleInfo.hasActiveRun) {
                    "Нельзя удалить модуль с активными запусками. Сначала остановите запуск."
                }

                lifecycleSupport.deleteWorkingCopyForModule(connection, normalizedSchema, moduleInfo.moduleId)

                lifecycleSupport.deleteModuleCascade(connection, normalizedSchema, moduleInfo.moduleId)

                connection.commit()
                return DeleteModuleResult(
                    moduleCode = moduleCode,
                    moduleId = moduleInfo.moduleId,
                    deletedBy = actorId,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun loadModuleForSave(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseModuleForSave {
        return lifecycleSupport.loadModuleForSave(connection, normalizedSchema, moduleCode, actorId, actorSource)
    }

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseModuleStore =
            DatabaseModuleStore(
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(config.jdbcUrl),
                    requireNotNull(config.username),
                    requireNotNull(config.password),
                ),
                schema = config.schemaName(),
            )
    }
}

package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.SaveModuleRequest

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
    private val mutationSupport = DatabaseModuleStoreMutationSupport(
        connectionProvider = connectionProvider,
        schema = schema,
        querySupport = querySupport,
        lifecycleSupport = lifecycleSupport,
        revisionWriter = revisionWriter,
    )

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
    ) = mutationSupport.saveWorkingCopy(moduleCode, actorId, actorSource, actorDisplayName, request)

    override fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) = mutationSupport.discardWorkingCopy(moduleCode, actorId, actorSource)

    override fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult = mutationSupport.publishWorkingCopy(moduleCode, actorId, actorSource, actorDisplayName)

    override fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        request: RegistryModuleDraft,
    ): RegistryModuleCreationResult = mutationSupport.createModule(
        moduleCode = moduleCode,
        actorId = actorId,
        actorSource = actorSource,
        actorDisplayName = actorDisplayName,
        originKind = originKind,
        request = request,
    )

    override fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult = mutationSupport.deleteModule(moduleCode, actorId)

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

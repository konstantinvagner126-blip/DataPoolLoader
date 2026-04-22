package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.nio.file.Path

class ModuleRegistry(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val appsRoot: Path? = null,
    private val validationService: ModuleValidationService = ModuleValidationService(),
) {
    private val mapper = configLoader.objectMapper()
    private val metadataSupport = ModuleRegistryMetadataSupport(mapper)
    private val sqlFileSupport = ModuleRegistrySqlFileSupport(mapper)
    private val catalogSupport = ModuleRegistryCatalogSupport(
        configLoader = configLoader,
        appsRoot = appsRoot,
        validationService = validationService,
        metadataSupport = metadataSupport,
        sqlFileSupport = sqlFileSupport,
    )
    private val persistenceSupport = ModuleRegistryPersistenceSupport(
        metadataSupport = metadataSupport,
        sqlFileSupport = sqlFileSupport,
    )

    fun listModules(includeHidden: Boolean = false): List<ModuleDescriptor> = catalogSupport.listModules(includeHidden)

    fun appsRootStatus(): AppsRootStatusResponse = catalogSupport.appsRootStatus()

    fun getModule(moduleId: String): ModuleDescriptor =
        listModules(includeHidden = true).firstOrNull { it.id == moduleId }
            ?: throw ModuleNotFoundException(moduleId)

    fun loadModuleDetails(moduleId: String): ModuleDetailsResponse = persistenceSupport.loadModuleDetails(getModule(moduleId))

    fun saveModule(moduleId: String, request: SaveModuleRequest) {
        persistenceSupport.saveModule(getModule(moduleId), request)
    }

    fun extractSqlReferences(configText: String): List<String> {
        return sqlFileSupport.extractSqlReferences(configText)
    }

    fun resolveSqlPath(module: ModuleDescriptor, sqlRef: String): Path? {
        return sqlFileSupport.resolveSqlPath(module, sqlRef)
    }
}

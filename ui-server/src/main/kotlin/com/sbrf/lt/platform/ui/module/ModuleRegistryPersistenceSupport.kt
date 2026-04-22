package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.toMetadataDescriptorResponse
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class ModuleRegistryPersistenceSupport(
    private val metadataSupport: ModuleRegistryMetadataSupport,
    private val sqlFileSupport: ModuleRegistrySqlFileSupport,
) {
    fun loadModuleDetails(module: ModuleDescriptor): ModuleDetailsResponse {
        val configText = module.configFile.takeIf { java.nio.file.Files.exists(it) }?.readText() ?: ""
        val sqlFiles = sqlFileSupport.loadManagedSqlFiles(module, configText)
        return ModuleDetailsResponse(
            id = module.id,
            descriptor = module.toMetadataDescriptorResponse(),
            validationStatus = module.validationStatus,
            validationIssues = module.validationIssues,
            configPath = module.configFile.toString(),
            configText = configText,
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

    fun saveModule(module: ModuleDescriptor, request: SaveModuleRequest) {
        val managedFilesBeforeSave = sqlFileSupport.managedSqlReferences(module, request.configText) +
            sqlFileSupport.discoverSqlCatalogKeys(module)
        module.configFile.parent.createDirectories()
        module.configFile.writeText(request.configText)
        metadataSupport.writeMetadata(module, request)

        request.sqlFiles.toSortedMap().forEach { (sqlRef, content) ->
            val file = sqlFileSupport.resolveSqlPath(module, sqlRef)
                ?.takeIf { sqlFileSupport.isManagedSqlPath(module, it) }
                ?: return@forEach
            file.parent?.createDirectories()
            file.writeText(content)
        }

        managedFilesBeforeSave
            .filterNot { request.sqlFiles.containsKey(it) }
            .forEach { sqlRef ->
                val file = sqlFileSupport.resolveSqlPath(module, sqlRef)
                    ?.takeIf { sqlFileSupport.isManagedSqlPath(module, it) }
                    ?: return@forEach
                file.deleteIfExists()
            }
    }
}

package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.fasterxml.jackson.databind.JsonNode
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.datapool.module.validation.ModuleValidationIssue
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.datapool.module.validation.ModuleValidationSeverity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ModuleRegistry(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val appsRoot: Path? = null,
    private val validationService: ModuleValidationService = ModuleValidationService(),
) {
    private val mapper = configLoader.objectMapper()

    fun listModules(includeHidden: Boolean = false): List<ModuleDescriptor> {
        val appsDir = appsRoot?.takeIf { Files.exists(it) && Files.isDirectory(it) } ?: return emptyList()

        val appDirs = Files.list(appsDir).use { paths -> paths.toList() }
        return appDirs
            .filter { Files.isDirectory(it) }
            .filter { it.fileName.toString() != "ui" }
            .filter { Files.exists(it.resolve("build.gradle.kts")) }
            .map { appDir -> buildModuleDescriptor(appDir) }
            .filter { includeHidden || !it.hiddenFromUi }
            .sortedBy { it.id }
    }

    fun appsRootStatus(): AppsRootStatusResponse {
        val root = appsRoot
        if (root == null) {
            return AppsRootStatusResponse(
                mode = "NOT_CONFIGURED",
                message = "Путь ui.appsRoot не задан. Укажи абсолютный путь к каталогу apps в ui-application.yml.",
            )
        }
        if (!Files.exists(root)) {
            return AppsRootStatusResponse(
                mode = "NOT_FOUND",
                configuredPath = root.toString(),
                message = "Каталог apps по указанному пути не найден. Проверь ui.appsRoot в ui-application.yml.",
            )
        }
        if (!Files.isDirectory(root)) {
            return AppsRootStatusResponse(
                mode = "NOT_DIRECTORY",
                configuredPath = root.toString(),
                message = "ui.appsRoot должен указывать на каталог apps, а не на файл.",
            )
        }

        val modules = listModules()
        val moduleCount = modules.size
        val invalidCount = modules.count { it.validationStatus == "INVALID" }
        val warningCount = modules.count { it.validationStatus == "WARNING" }
        return AppsRootStatusResponse(
            mode = "READY",
            configuredPath = root.toString(),
            message = if (moduleCount > 0) {
                buildString {
                    append("Каталог apps найден. Доступно модулей: $moduleCount.")
                    if (invalidCount > 0) {
                        append(" С проблемами: $invalidCount.")
                    } else if (warningCount > 0) {
                        append(" С предупреждениями: $warningCount.")
                    }
                }
            } else {
                "Каталог apps найден, но подходящие модули в нем не обнаружены."
            },
        )
    }

    fun getModule(moduleId: String): ModuleDescriptor =
        listModules(includeHidden = true).firstOrNull { it.id == moduleId }
            ?: error("Модуль '$moduleId' не найден.")

    fun loadModuleDetails(moduleId: String): ModuleDetailsResponse {
        val module = getModule(moduleId)
        val configText = module.configFile.takeIf { Files.exists(it) }?.readText() ?: ""
        val sqlFiles = loadManagedSqlFiles(module, configText)
        return ModuleDetailsResponse(
            id = module.id,
            title = module.title,
            description = module.description,
            tags = module.tags,
            hiddenFromUi = module.hiddenFromUi,
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

    fun saveModule(moduleId: String, request: SaveModuleRequest) {
        val module = getModule(moduleId)
        val managedFilesBeforeSave = managedSqlReferences(module, request.configText) + discoverSqlCatalogKeys(module)
        module.configFile.parent.createDirectories()
        module.configFile.writeText(request.configText)
        writeMetadata(module, request)

        request.sqlFiles.toSortedMap().forEach { (sqlRef, content) ->
            val file = resolveSqlPath(module, sqlRef)
                ?.takeIf { isManagedSqlPath(module, it) }
                ?: return@forEach
            file.parent?.createDirectories()
            file.writeText(content)
        }

        managedFilesBeforeSave
            .filterNot { request.sqlFiles.containsKey(it) }
            .forEach { sqlRef ->
                val file = resolveSqlPath(module, sqlRef)
                    ?.takeIf { isManagedSqlPath(module, it) }
                    ?: return@forEach
                file.deleteIfExists()
            }
    }

    fun extractSqlReferences(configText: String): List<String> {
        return SqlFileReferenceExtractor.extractOrEmpty(configText, mapper).map { it.path }
    }

    fun resolveSqlPath(module: ModuleDescriptor, sqlRef: String): Path? {
        val trimmed = sqlRef.trim()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("classpath:")) {
            module.resourcesDir.resolve(trimmed.removePrefix("classpath:").removePrefix("/")).normalize()
        } else {
            val path = Path.of(trimmed)
            if (path.isAbsolute) path else module.configFile.parent.resolve(path).normalize()
        }
    }

    private fun loadManagedSqlFiles(module: ModuleDescriptor, configText: String): List<ModuleFileContent> {
        val discoveredKeys = discoverSqlCatalogKeys(module)
        val referencedEntries = SqlFileReferenceExtractor.extractOrEmpty(configText, mapper)
        val referencedKeys = referencedEntries.map { it.path }.filter { sqlRef ->
            resolveSqlPath(module, sqlRef)?.let { isManagedSqlPath(module, it) } == true
        }
        val labelsByPath = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, mapper)
        val allKeys = (discoveredKeys + referencedKeys).toSortedSet()
        return allKeys.map { sqlRef ->
            val path = resolveSqlPath(module, sqlRef)
            ModuleFileContent(
                label = labelsByPath[sqlRef] ?: defaultSqlLabel(sqlRef),
                path = sqlRef,
                content = path?.takeIf { Files.exists(it) }?.readText() ?: "",
                exists = path?.let { Files.exists(it) } == true,
            )
        }
    }

    private fun discoverSqlCatalogKeys(module: ModuleDescriptor): Set<String> {
        val sqlRoot = module.resourcesDir.resolve("sql")
        if (!Files.exists(sqlRoot) || !Files.isDirectory(sqlRoot)) {
            return emptySet()
        }
        return Files.walk(sqlRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".sql", ignoreCase = true) }
                .map { path ->
                    val relative = module.resourcesDir.relativize(path).toString().replace('\\', '/')
                    "classpath:$relative"
                }
                .toList()
                .toSet()
        }
    }

    private fun managedSqlReferences(module: ModuleDescriptor, configText: String): Set<String> =
        SqlFileReferenceExtractor.extractOrEmpty(configText, mapper)
            .map { it.path }
            .filter { sqlRef ->
                resolveSqlPath(module, sqlRef)?.let { isManagedSqlPath(module, it) } == true
            }
            .toSet()

    private fun isManagedSqlPath(module: ModuleDescriptor, path: Path): Boolean =
        path.normalize().startsWith(module.resourcesDir.normalize())

    private fun defaultSqlLabel(sqlRef: String): String =
        sqlRef.substringAfterLast('/').substringAfterLast('\\').ifBlank { sqlRef }

    private fun buildModuleDescriptor(appDir: Path): ModuleDescriptor {
        val resourcesDir = appDir.resolve("src/main/resources")
        val configFile = resourcesDir.resolve("application.yml")
        val metadataFile = appDir.resolve("ui-module.yml")
        val metadata = loadMetadata(metadataFile)
        val configText = configFile.takeIf { it.exists() }?.readText().orEmpty()
        val validation = validationService.validate(
            configText = configText,
            sqlReferenceExists = { entry ->
                val sqlPath = resolveSqlPath(configFile, resourcesDir, entry.path)
                sqlPath != null && Files.exists(sqlPath)
            },
            additionalIssues = buildList {
                if (!configFile.exists()) {
                    add(
                        ModuleValidationIssue(
                            severity = ModuleValidationSeverity.ERROR,
                            message = "Не найден файл src/main/resources/application.yml.",
                        ),
                    )
                }
                metadata.issue?.let { issue ->
                    add(
                        ModuleValidationIssue(
                            severity = ModuleValidationSeverity.valueOf(issue.severity),
                            message = issue.message,
                        ),
                    )
                }
            },
        )

        return ModuleDescriptor(
            id = appDir.fileName.toString(),
            title = metadata.title ?: appDir.fileName.toString(),
            description = metadata.description,
            tags = metadata.tags,
            hiddenFromUi = metadata.hiddenFromUi,
            validationStatus = validation.status.name,
            validationIssues = validation.issues.map { issue ->
                ModuleValidationIssueResponse(
                    severity = issue.severity.name,
                    message = issue.message,
                )
            },
            configFile = configFile,
            resourcesDir = resourcesDir,
        )
    }

    private fun loadMetadata(metadataFile: Path): ModuleMetadataResult {
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

    private fun resolveSqlPath(configFile: Path, resourcesDir: Path, sqlRef: String): Path? {
        val trimmed = sqlRef.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return if (trimmed.startsWith("classpath:")) {
            resourcesDir.resolve(trimmed.removePrefix("classpath:").removePrefix("/")).normalize()
        } else {
            val path = Path.of(trimmed)
            if (path.isAbsolute) path else configFile.parent.resolve(path).normalize()
        }
    }

    private fun writeMetadata(module: ModuleDescriptor, request: SaveModuleRequest) {
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

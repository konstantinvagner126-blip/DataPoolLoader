package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ModuleRegistry(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val appsRoot: Path? = null,
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
        val sqlFiles = extractSqlFileEntriesOrEmpty(configText).map { entry ->
            val sqlRef = entry.path
            val path = resolveSqlPath(module, sqlRef)
            ModuleFileContent(
                label = entry.label,
                path = sqlRef,
                content = path?.takeIf { Files.exists(it) }?.readText() ?: "",
                exists = path?.let { Files.exists(it) } == true,
            )
        }
        return ModuleDetailsResponse(
            id = module.id,
            title = module.title,
            description = module.description,
            tags = module.tags,
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
        )
    }

    fun saveModule(moduleId: String, request: SaveModuleRequest) {
        val module = getModule(moduleId)
        module.configFile.parent.createDirectories()
        module.configFile.writeText(request.configText)

        extractSqlFileEntriesOrEmpty(request.configText).forEach { entry ->
            val content = request.sqlFiles[entry.path] ?: return@forEach
            val file = resolveSqlPath(module, entry.path) ?: return@forEach
            file.parent?.createDirectories()
            file.writeText(content)
        }
    }

    fun extractSqlReferences(configText: String): List<String> {
        return extractSqlFileEntriesOrEmpty(configText).map { it.path }
    }

    private fun extractSqlFileEntriesOrEmpty(configText: String): List<SqlFileEntry> =
        try {
            extractSqlFileEntries(configText)
        } catch (_: JsonProcessingException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    private fun extractSqlFileEntries(configText: String): List<SqlFileEntry> {
        if (configText.isBlank()) {
            return emptyList()
        }
        val root = mapper.readTree(configText) ?: return emptyList()
        val app = root.path("app")
        val refs = linkedMapOf<String, SqlFileEntry>()
        app.path("commonSqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
            refs[path] = SqlFileEntry(label = "Общий SQL", path = path)
        }
        app.path("sources").takeIf { it.isArray }?.forEach { source ->
            val sourceName = source.path("name").takeIf { it.isTextual }?.asText()?.ifBlank { "source" } ?: "source"
            source.path("sqlFile").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let { path ->
                refs[path] = SqlFileEntry(label = "Источник: $sourceName", path = path)
            }
        }
        return refs.values.toList()
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

    private fun buildModuleDescriptor(appDir: Path): ModuleDescriptor {
        val resourcesDir = appDir.resolve("src/main/resources")
        val configFile = resourcesDir.resolve("application.yml")
        val metadataFile = appDir.resolve("ui-module.yml")
        val metadata = loadMetadata(metadataFile)
        val issues = mutableListOf<ModuleValidationIssueResponse>()

        if (metadata.issue != null) {
            issues += metadata.issue
        }

        val configText = if (configFile.exists()) {
            configFile.readText()
        } else {
            issues += ModuleValidationIssueResponse(
                severity = "ERROR",
                message = "Не найден файл src/main/resources/application.yml.",
            )
            ""
        }

        var parsedRoot: JsonNode? = null
        if (configText.isNotBlank()) {
            try {
                parsedRoot = mapper.readTree(configText)
            } catch (error: Exception) {
                issues += ModuleValidationIssueResponse(
                    severity = "ERROR",
                    message = "application.yml не удалось разобрать: ${error.message ?: "ошибка синтаксиса YAML"}",
                )
            }
        }

        if (parsedRoot != null) {
            val sqlRefs = extractSqlFileEntriesOrEmpty(configText)
            sqlRefs.forEach { entry ->
                val sqlPath = resolveSqlPath(configFile, resourcesDir, entry.path)
                if (sqlPath == null || !Files.exists(sqlPath)) {
                    issues += ModuleValidationIssueResponse(
                        severity = "ERROR",
                        message = "Не найден SQL-файл ${entry.path} (${entry.label}).",
                    )
                }
            }

            val duplicateSourceNames = parsedRoot.path("app").path("sources")
                .takeIf { it.isArray }
                ?.mapNotNull { source ->
                    source.path("name").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
                }
                ?.groupingBy { it }
                ?.eachCount()
                ?.filterValues { it > 1 }
                ?.keys
                ?.sorted()
                .orEmpty()

            if (duplicateSourceNames.isNotEmpty()) {
                issues += ModuleValidationIssueResponse(
                    severity = "WARNING",
                    message = "Повторяются имена sources: ${duplicateSourceNames.joinToString(", ")}.",
                )
            }
        }

        val validationStatus = when {
            issues.any { it.severity == "ERROR" } -> "INVALID"
            issues.any { it.severity == "WARNING" } -> "WARNING"
            else -> "VALID"
        }

        return ModuleDescriptor(
            id = appDir.fileName.toString(),
            title = metadata.title ?: appDir.fileName.toString(),
            description = metadata.description,
            tags = metadata.tags,
            hiddenFromUi = metadata.hiddenFromUi,
            validationStatus = validationStatus,
            validationIssues = issues,
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

    private data class SqlFileEntry(
        val label: String,
        val path: String,
    )

    private data class ModuleMetadataResult(
        val title: String? = null,
        val description: String? = null,
        val tags: List<String> = emptyList(),
        val hiddenFromUi: Boolean = false,
        val issue: ModuleValidationIssueResponse? = null,
    )
}

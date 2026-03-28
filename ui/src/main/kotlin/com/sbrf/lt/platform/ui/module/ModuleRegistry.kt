package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ModuleRegistry(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val appsRoot: Path? = null,
) {
    private val mapper = configLoader.objectMapper()

    fun listModules(): List<ModuleDescriptor> {
        val appsDir = appsRoot?.takeIf { Files.exists(it) && Files.isDirectory(it) } ?: return emptyList()

        val appDirs = Files.list(appsDir).use { paths -> paths.toList() }
        return appDirs
            .filter { Files.isDirectory(it) }
            .filter { it.fileName.toString() != "ui" }
            .filter { Files.exists(it.resolve("build.gradle.kts")) }
            .mapNotNull { appDir ->
                val configFile = appDir.resolve("src/main/resources/application.yml")
                val resourcesDir = appDir.resolve("src/main/resources")
                if (!Files.exists(configFile)) {
                    null
                } else {
                    ModuleDescriptor(
                        id = appDir.fileName.toString(),
                        title = appDir.fileName.toString(),
                        configFile = configFile,
                        resourcesDir = resourcesDir,
                    )
                }
            }
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

        val moduleCount = listModules().size
        return AppsRootStatusResponse(
            mode = "READY",
            configuredPath = root.toString(),
            message = if (moduleCount > 0) {
                "Каталог apps найден. Доступно модулей: $moduleCount."
            } else {
                "Каталог apps найден, но подходящие модули в нем не обнаружены."
            },
        )
    }

    fun getModule(moduleId: String): ModuleDescriptor =
        listModules().firstOrNull { it.id == moduleId }
            ?: error("Модуль '$moduleId' не найден.")

    fun loadModuleDetails(moduleId: String): ModuleDetailsResponse {
        val module = getModule(moduleId)
        val configText = module.configFile.readText()
        val sqlFiles = extractSqlFileEntries(configText).map { entry ->
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
        module.configFile.writeText(request.configText)

        extractSqlFileEntries(request.configText).forEach { entry ->
            val content = request.sqlFiles[entry.path] ?: return@forEach
            val file = resolveSqlPath(module, entry.path) ?: return@forEach
            file.parent?.createDirectories()
            file.writeText(content)
        }
    }

    fun extractSqlReferences(configText: String): List<String> {
        return extractSqlFileEntries(configText).map { it.path }
    }

    private fun extractSqlFileEntries(configText: String): List<SqlFileEntry> {
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

    private data class SqlFileEntry(
        val label: String,
        val path: String,
    )
}

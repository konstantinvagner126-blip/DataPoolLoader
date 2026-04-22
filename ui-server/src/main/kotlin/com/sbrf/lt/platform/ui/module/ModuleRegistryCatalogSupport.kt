package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.module.validation.ModuleValidationIssue
import com.sbrf.lt.datapool.module.validation.ModuleValidationService
import com.sbrf.lt.datapool.module.validation.ModuleValidationSeverity
import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class ModuleRegistryCatalogSupport(
    private val configLoader: ConfigLoader,
    private val appsRoot: Path?,
    private val validationService: ModuleValidationService,
    private val metadataSupport: ModuleRegistryMetadataSupport,
    private val sqlFileSupport: ModuleRegistrySqlFileSupport,
) {
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

    private fun buildModuleDescriptor(appDir: Path): ModuleDescriptor {
        val resourcesDir = appDir.resolve("src/main/resources")
        val configFile = resourcesDir.resolve("application.yml")
        val metadataFile = appDir.resolve("ui-module.yml")
        val metadata = metadataSupport.loadMetadata(metadataFile)
        val configText = configFile.takeIf { it.exists() }?.readText().orEmpty()
        val validation = validationService.validate(
            configText = configText,
            sqlReferenceExists = { entry ->
                val sqlPath = sqlFileSupport.resolveSqlPath(configFile, resourcesDir, entry.path)
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
}

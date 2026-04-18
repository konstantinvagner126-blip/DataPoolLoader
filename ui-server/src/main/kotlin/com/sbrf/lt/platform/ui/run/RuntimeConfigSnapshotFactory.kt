package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Создает runtime snapshot из YAML-конфига и набора SQL-файлов.
 * При необходимости materialize'ит SQL references во временную директорию перед разбором `AppConfig`.
 */
class RuntimeConfigSnapshotFactory(
    private val configLoader: ConfigLoader = ConfigLoader(),
) {

    fun createSnapshot(
        moduleCode: String?,
        moduleTitle: String?,
        configText: String,
        sqlFiles: Map<String, String>,
        launchSourceKind: String,
        configLocation: String,
        executionSnapshotId: String? = null,
        fallbackSqlResolver: (String) -> String? = { null },
    ): RuntimeModuleSnapshot {
        val tempDir = Files.createTempDirectory("datapool-ui-runtime-${moduleCode ?: "module"}-")
        val tempConfig = prepareWorkingCopy(
            configText = configText,
            sqlFiles = sqlFiles,
            tempDir = tempDir,
            fallbackSqlResolver = fallbackSqlResolver,
        )
        val appConfig = configLoader.load(tempConfig)
        return RuntimeModuleSnapshot(
            moduleCode = moduleCode,
            moduleTitle = moduleTitle,
            configYaml = configText,
            sqlFiles = sqlFiles.toSortedMap(),
            appConfig = appConfig,
            launchSourceKind = launchSourceKind,
            executionSnapshotId = executionSnapshotId,
            configLocation = configLocation,
        )
    }

    private fun prepareWorkingCopy(
        configText: String,
        sqlFiles: Map<String, String>,
        tempDir: Path,
        fallbackSqlResolver: (String) -> String?,
    ): Path {
        val root = configLoader.objectMapper().readTree(configText) as ObjectNode
        val appNode = root.path("app") as ObjectNode

        appNode.path("commonSqlFile").takeIf { it.isTextual }?.also { node ->
            rewriteSqlReference(sqlFiles, tempDir, node.asText(), appNode, "commonSqlFile", fallbackSqlResolver)
        }

        appNode.path("sources").takeIf { it.isArray }?.forEach { sourceNode ->
            sourceNode.path("sqlFile").takeIf { it.isTextual }?.also { node ->
                rewriteSqlReference(sqlFiles, tempDir, node.asText(), sourceNode as ObjectNode, "sqlFile", fallbackSqlResolver)
            }
        }

        val configPath = tempDir.resolve("application.yml")
        configPath.writeText(configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root))
        return configPath
    }

    private fun rewriteSqlReference(
        sqlFiles: Map<String, String>,
        tempDir: Path,
        originalRef: String,
        objectNode: ObjectNode,
        fieldName: String,
        fallbackSqlResolver: (String) -> String?,
    ) {
        val content = sqlFiles[originalRef]
            ?: fallbackSqlResolver(originalRef)
            ?: return

        val relativePath = when {
            originalRef.startsWith("classpath:") -> originalRef.removePrefix("classpath:").removePrefix("/")
            else -> originalRef.removePrefix("./")
        }
        val target = tempDir.resolve(relativePath).normalize()
        target.parent?.createDirectories()
        target.writeText(content)
        objectNode.put(fieldName, relativePath)
    }
}

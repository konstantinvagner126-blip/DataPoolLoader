package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class RuntimeConfigWorkingCopySupport(
    configLoader: ConfigLoader = ConfigLoader(),
) {
    private val objectMapper: ObjectMapper = configLoader.objectMapper()

    fun prepareWorkingCopy(
        configText: String,
        sqlFiles: Map<String, String>,
        tempDir: Path,
        fallbackSqlResolver: (String) -> String?,
    ): Path {
        val root = objectMapper.readTree(configText) as ObjectNode
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
        configPath.writeText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
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

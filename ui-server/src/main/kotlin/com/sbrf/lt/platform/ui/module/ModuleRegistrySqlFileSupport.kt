package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class ModuleRegistrySqlFileSupport(
    private val mapper: ObjectMapper,
) {
    fun extractSqlReferences(configText: String): List<String> =
        SqlFileReferenceExtractor.extractOrEmpty(configText, mapper).map { it.path }

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

    fun resolveSqlPath(configFile: Path, resourcesDir: Path, sqlRef: String): Path? {
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

    fun loadManagedSqlFiles(module: ModuleDescriptor, configText: String): List<ModuleFileContent> {
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

    fun discoverSqlCatalogKeys(module: ModuleDescriptor): Set<String> {
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

    fun managedSqlReferences(module: ModuleDescriptor, configText: String): Set<String> =
        SqlFileReferenceExtractor.extractOrEmpty(configText, mapper)
            .map { it.path }
            .filter { sqlRef ->
                resolveSqlPath(module, sqlRef)?.let { isManagedSqlPath(module, it) } == true
            }
            .toSet()

    fun isManagedSqlPath(module: ModuleDescriptor, path: Path): Boolean =
        path.normalize().startsWith(module.resourcesDir.normalize())

    private fun defaultSqlLabel(sqlRef: String): String =
        sqlRef.substringAfterLast('/').substringAfterLast('\\').ifBlank { sqlRef }
}

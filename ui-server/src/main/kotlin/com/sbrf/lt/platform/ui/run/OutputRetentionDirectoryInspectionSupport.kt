package com.sbrf.lt.platform.ui.run

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal class OutputRetentionDirectoryInspectionSupport {
    fun inspectDirectories(rawPaths: List<String>): List<OutputRetentionDirectoryEntry> =
        rawPaths.map(::inspectDirectory)

    private fun inspectDirectory(rawPath: String): OutputRetentionDirectoryEntry {
        val normalizedPath = runCatching { Path.of(rawPath).toAbsolutePath().normalize() }.getOrNull()
        val exists = normalizedPath != null && Files.exists(normalizedPath)
        val sizeBytes = normalizedPath
            ?.takeIf { exists }
            ?.let(::calculateSize)
            ?: 0L
        return OutputRetentionDirectoryEntry(
            rawPath = rawPath,
            normalizedPath = normalizedPath,
            exists = exists,
            sizeBytes = sizeBytes,
        )
    }

    private fun calculateSize(path: Path): Long {
        if (!Files.exists(path)) return 0L
        if (!path.isDirectory()) {
            return runCatching { Files.size(path) }.getOrDefault(0L)
        }
        Files.walk(path).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .mapToLong { file ->
                    runCatching { Files.size(file) }.getOrDefault(0L)
                }
                .sum()
        }
    }
}

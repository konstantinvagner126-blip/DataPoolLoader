package com.sbrf.lt.platform.ui.run

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

internal class OutputRetentionDeletionSupport {
    fun deleteDirectories(entries: List<OutputRetentionDirectoryEntry>): OutputRetentionDeleteResult {
        var deletedDirs = 0
        var missingDirs = 0
        var bytesFreed = 0L

        entries.forEach { entry ->
            val path = entry.normalizedPath
            if (path == null || !Files.exists(path)) {
                missingDirs += 1
                return@forEach
            }
            deleteRecursively(path)
            deletedDirs += 1
            bytesFreed += entry.sizeBytes
        }

        return OutputRetentionDeleteResult(
            deletedDirs = deletedDirs,
            missingDirs = missingDirs,
            bytesFreed = bytesFreed,
        )
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder<Path>())
            .use { stream ->
                stream.forEach { current ->
                    Files.deleteIfExists(current)
                }
            }
    }
}

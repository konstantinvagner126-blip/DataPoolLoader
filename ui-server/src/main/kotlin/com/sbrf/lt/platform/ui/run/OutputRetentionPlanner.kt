package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Comparator
import kotlin.io.path.isDirectory

data class OutputRetentionRunRef(
    val moduleCode: String,
    val requestedAt: Instant,
    val outputDir: String,
)

internal data class OutputRetentionDirectoryEntry(
    val rawPath: String,
    val normalizedPath: Path?,
    val exists: Boolean,
    val sizeBytes: Long,
)

internal data class OutputRetentionPlan(
    val runs: List<OutputRetentionRunRef>,
    val directories: List<OutputRetentionDirectoryEntry>,
    val modules: List<OutputRetentionModuleResponse>,
) {
    val totalRunsAffected: Int
        get() = runs.size

    val totalOutputDirsToDelete: Int
        get() = directories.count { it.exists }

    val totalMissingOutputDirs: Int
        get() = directories.count { !it.exists }

    val totalBytesToFree: Long
        get() = directories.sumOf { it.sizeBytes }
}

internal data class OutputRetentionDeleteResult(
    val deletedDirs: Int,
    val missingDirs: Int,
    val bytesFreed: Long,
)

internal object OutputRetentionPlanner {
    fun buildPlan(candidates: List<OutputRetentionRunRef>): OutputRetentionPlan {
        val normalizedRuns = candidates
            .mapNotNull { candidate ->
                val trimmed = candidate.outputDir.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    candidate.copy(outputDir = trimmed)
                }
            }

        val directories = normalizedRuns
            .map { it.outputDir }
            .distinct()
            .map { rawPath -> inspectDirectory(rawPath) }

        val directoriesByPath = directories.associateBy { it.rawPath }

        val modules = normalizedRuns
            .groupBy { it.moduleCode }
            .map { (moduleCode, runs) ->
                val moduleDirectories = runs
                    .mapNotNull { directoriesByPath[it.outputDir] }
                    .distinctBy { it.rawPath }
                OutputRetentionModuleResponse(
                    moduleCode = moduleCode,
                    totalRunsAffected = runs.size,
                    totalOutputDirsToDelete = moduleDirectories.count { it.exists },
                    totalBytesToFree = moduleDirectories.sumOf { it.sizeBytes },
                    oldestRequestedAt = runs.minOfOrNull { it.requestedAt },
                    newestRequestedAt = runs.maxOfOrNull { it.requestedAt },
                )
            }
            .sortedWith(
                compareByDescending<OutputRetentionModuleResponse> { it.totalOutputDirsToDelete }
                    .thenByDescending { it.totalBytesToFree }
                    .thenBy { it.moduleCode },
            )

        return OutputRetentionPlan(
            runs = normalizedRuns,
            directories = directories.sortedBy { it.rawPath },
            modules = modules,
        )
    }

    fun delete(plan: OutputRetentionPlan): OutputRetentionDeleteResult {
        var deletedDirs = 0
        var missingDirs = 0
        var bytesFreed = 0L

        plan.directories.forEach { entry ->
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

    private fun inspectDirectory(rawPath: String): OutputRetentionDirectoryEntry {
        val normalizedPath = runCatching { Path.of(rawPath).toAbsolutePath().normalize() }.getOrNull()
        val exists = normalizedPath != null && Files.exists(normalizedPath)
        val sizeBytes = normalizedPath
            ?.takeIf { exists }
            ?.let { calculateSize(it) }
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
                .mapToLong {
                    runCatching { Files.size(it) }.getOrDefault(0L)
                }
                .sum()
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .use { stream ->
                stream.forEach { current ->
                    Files.deleteIfExists(current)
                }
            }
    }
}

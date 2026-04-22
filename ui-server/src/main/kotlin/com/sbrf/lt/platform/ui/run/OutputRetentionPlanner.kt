package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse
import java.nio.file.Path
import java.time.Instant

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
    private val directoryInspectionSupport = OutputRetentionDirectoryInspectionSupport()
    private val deletionSupport = OutputRetentionDeletionSupport()

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
            .let(directoryInspectionSupport::inspectDirectories)

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

    fun delete(plan: OutputRetentionPlan): OutputRetentionDeleteResult =
        deletionSupport.deleteDirectories(plan.directories)
}

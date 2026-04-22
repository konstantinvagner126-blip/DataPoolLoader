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
    private val runNormalizationSupport = OutputRetentionRunNormalizationSupport()
    private val moduleSummarySupport = OutputRetentionModuleSummarySupport()
    private val directoryInspectionSupport = OutputRetentionDirectoryInspectionSupport()
    private val deletionSupport = OutputRetentionDeletionSupport()

    fun buildPlan(candidates: List<OutputRetentionRunRef>): OutputRetentionPlan {
        val normalizedRuns = runNormalizationSupport.normalizeRuns(candidates)

        val directories = normalizedRuns
            .map { it.outputDir }
            .distinct()
            .let(directoryInspectionSupport::inspectDirectories)

        val directoriesByPath = directories.associateBy { it.rawPath }

        val modules = moduleSummarySupport.buildModules(normalizedRuns, directoriesByPath)

        return OutputRetentionPlan(
            runs = normalizedRuns,
            directories = directories.sortedBy { it.rawPath },
            modules = modules,
        )
    }

    fun delete(plan: OutputRetentionPlan): OutputRetentionDeleteResult =
        deletionSupport.deleteDirectories(plan.directories)
}

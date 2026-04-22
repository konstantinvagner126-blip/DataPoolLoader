package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse

internal class OutputRetentionModuleSummarySupport {
    fun buildModules(
        runs: List<OutputRetentionRunRef>,
        directoriesByPath: Map<String, OutputRetentionDirectoryEntry>,
    ): List<OutputRetentionModuleResponse> =
        runs.groupBy { it.moduleCode }
            .map { (moduleCode, moduleRuns) ->
                val moduleDirectories = moduleRuns
                    .mapNotNull { directoriesByPath[it.outputDir] }
                    .distinctBy { it.rawPath }
                OutputRetentionModuleResponse(
                    moduleCode = moduleCode,
                    totalRunsAffected = moduleRuns.size,
                    totalOutputDirsToDelete = moduleDirectories.count { it.exists },
                    totalBytesToFree = moduleDirectories.sumOf { it.sizeBytes },
                    oldestRequestedAt = moduleRuns.minOfOrNull { it.requestedAt },
                    newestRequestedAt = moduleRuns.maxOfOrNull { it.requestedAt },
                )
            }
            .sortedWith(
                compareByDescending<OutputRetentionModuleResponse> { it.totalOutputDirsToDelete }
                    .thenByDescending { it.totalBytesToFree }
                    .thenBy { it.moduleCode },
            )
}

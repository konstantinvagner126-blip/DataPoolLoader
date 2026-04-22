package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot

internal fun projectFilesRunArtifacts(
    run: UiRunSnapshot,
    sourceResults: List<ModuleRunSourceResultResponse>,
): List<ModuleRunArtifactResponse> {
    val outputDir = run.outputDir ?: return emptyList()
    val artifacts = mutableListOf<ModuleRunArtifactResponse>()

    artifacts += createFilesRunArtifact(
        artifactKind = "MERGED_OUTPUT",
        artifactKey = "merged",
        filePath = joinFilesRunOutputPath(outputDir, "merged.csv"),
    )

    if (!run.summaryJson.isNullOrBlank()) {
        artifacts += createFilesRunArtifact(
            artifactKind = "SUMMARY_JSON",
            artifactKey = "summary",
            filePath = joinFilesRunOutputPath(outputDir, "summary.json"),
        )
    }

    sourceResults
        .filter { it.status == "SUCCESS" }
        .sortedBy { it.sortOrder }
        .forEach { source ->
            artifacts += createFilesRunArtifact(
                artifactKind = "SOURCE_OUTPUT",
                artifactKey = source.sourceName,
                filePath = joinFilesRunOutputPath(outputDir, "${source.sourceName}.csv"),
            )
        }

    return artifacts
}

package com.sbrf.lt.platform.ui.run

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class DatabaseModuleRunArtifactSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
) {
    fun rememberArtifact(context: DatabaseModuleRunContext, path: Path, artifactKind: String, artifactKey: String) {
        val storageStatus = if (path.exists()) "PRESENT" else "MISSING"
        val fileSize = if (path.exists()) runExecutionStore.fileSize(path) else null
        runExecutionStore.upsertArtifact(
            runId = context.runId,
            artifactKind = artifactKind,
            artifactKey = artifactKey,
            filePath = path.toString(),
            storageStatus = storageStatus,
            fileSizeBytes = fileSize,
            contentHash = null,
        )
        context.artifactRefsByFileName[path.fileName.toString()] = DatabaseRunArtifactRef(
            artifactKind = artifactKind,
            artifactKey = artifactKey,
        )
    }

    fun markArtifactDeleted(context: DatabaseModuleRunContext, fileName: String) {
        context.artifactRefsByFileName[fileName]?.let { ref ->
            runExecutionStore.markArtifactDeleted(context.runId, ref.artifactKind, ref.artifactKey)
        }
    }

    fun loadSummaryJson(summaryFile: String?): String {
        if (summaryFile.isNullOrBlank()) {
            return "{}"
        }
        val path = Path.of(summaryFile)
        if (!path.exists()) {
            return "{}"
        }
        return runCatching {
            path.readText()
        }.getOrDefault("{}")
    }
}

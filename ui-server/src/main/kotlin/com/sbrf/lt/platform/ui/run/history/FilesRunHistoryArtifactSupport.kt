package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize

internal fun createFilesRunArtifact(
    artifactKind: String,
    artifactKey: String,
    filePath: String,
): ModuleRunArtifactResponse {
    val path = runCatching { Path.of(filePath) }.getOrNull()
    val exists = path?.let(Files::exists) == true
    return ModuleRunArtifactResponse(
        artifactKind = artifactKind,
        artifactKey = artifactKey,
        filePath = filePath,
        storageStatus = if (exists) "PRESENT" else "MISSING",
        fileSizeBytes = path?.takeIf(Files::exists)?.fileSize(),
    )
}

internal fun joinFilesRunOutputPath(outputDir: String, fileName: String): String {
    val base = outputDir.trim()
    if (base.isEmpty()) {
        return fileName
    }
    val separator = if (base.contains('\\') && !base.contains('/')) "\\" else "/"
    return "${base.trimEnd('/', '\\')}$separator$fileName"
}

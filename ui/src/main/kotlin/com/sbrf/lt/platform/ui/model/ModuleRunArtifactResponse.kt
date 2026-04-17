package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Артефакт, созданный или использованный во время запуска модуля.
 */
data class ModuleRunArtifactResponse(
    val runArtifactId: String? = null,
    val artifactKind: String,
    val artifactKey: String,
    val filePath: String,
    val storageStatus: String,
    val fileSizeBytes: Long? = null,
    val contentHash: String? = null,
    val createdAt: Instant? = null,
)

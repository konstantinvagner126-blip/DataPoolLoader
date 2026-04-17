package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Описание артефакта, который был создан или использован в рамках DB-запуска.
 */
data class DatabaseRunArtifactResponse(
    val runArtifactId: String,
    val artifactKind: String,
    val artifactKey: String,
    val filePath: String,
    val storageStatus: String,
    val fileSizeBytes: Long? = null,
    val contentHash: String? = null,
    val createdAt: Instant,
)

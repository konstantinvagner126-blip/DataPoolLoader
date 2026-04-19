package com.sbrf.lt.platform.ui.run

/**
 * Ссылка на артефакт запуска для последующего обновления его статуса.
 */
data class DatabaseRunArtifactRef(
    val artifactKind: String,
    val artifactKey: String,
)

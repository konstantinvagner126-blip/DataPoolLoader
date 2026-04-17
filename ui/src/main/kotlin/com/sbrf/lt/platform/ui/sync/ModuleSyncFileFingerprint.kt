package com.sbrf.lt.platform.ui.sync

/**
 * Быстрый файловый fingerprint модуля, используемый до пересчета канонического `content_hash`.
 */
data class ModuleSyncFileFingerprint(
    val trackedFiles: List<ModuleSyncTrackedFile> = emptyList(),
)

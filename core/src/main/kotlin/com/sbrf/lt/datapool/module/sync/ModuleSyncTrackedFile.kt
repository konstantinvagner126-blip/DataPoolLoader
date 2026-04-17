package com.sbrf.lt.datapool.module.sync

/**
 * Метаданные одного отслеживаемого файла модуля для быстрого precheck по `mtime/size`.
 */
data class ModuleSyncTrackedFile(
    val path: String,
    val exists: Boolean,
    val lastModifiedEpochMillis: Long? = null,
    val sizeBytes: Long? = null,
)

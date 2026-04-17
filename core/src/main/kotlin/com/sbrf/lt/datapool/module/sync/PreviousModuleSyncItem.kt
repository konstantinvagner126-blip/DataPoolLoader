package com.sbrf.lt.datapool.module.sync

/**
 * Последний сохраненный результат sync по модулю, нужный для быстрого precheck без чтения содержимого файлов.
 */
data class PreviousModuleSyncItem(
    val action: String,
    val status: String,
    val detectedHash: String,
    val resultRevisionId: String? = null,
    val filesystemFingerprint: ModuleSyncFileFingerprint? = null,
)

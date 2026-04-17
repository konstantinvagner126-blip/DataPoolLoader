package com.sbrf.lt.datapool.module.sync

/**
 * Результат обработки одного модуля в рамках запуска импорта `files -> database`.
 */
data class SyncItemResult(
    val moduleCode: String,
    val action: String,
    val status: String,
    val detectedHash: String,
    val resultRevisionId: String? = null,
    val errorMessage: String? = null,
    val details: Map<String, Any?> = emptyMap(),
)

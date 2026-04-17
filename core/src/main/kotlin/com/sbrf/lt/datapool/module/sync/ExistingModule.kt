package com.sbrf.lt.datapool.module.sync

/**
 * Текущее состояние DB-модуля, уже существующего в registry, для проверки конфликта импорта.
 */
internal data class ExistingModule(
    val moduleId: String,
    val currentRevisionId: String,
    val contentHash: String?,
)

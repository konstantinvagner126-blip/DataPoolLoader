package com.sbrf.lt.datapool.db.registry.model

/**
 * Результат создания DB-модуля в registry.
 */
data class RegistryModuleCreationResult(
    val moduleId: String,
    val moduleCode: String,
    val revisionId: String,
    val workingCopyId: String,
)

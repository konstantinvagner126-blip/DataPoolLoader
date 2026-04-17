package com.sbrf.lt.datapool.module.validation

/**
 * Описание отдельной проблемы, найденной в конфигурации модуля.
 */
data class ModuleValidationIssue(
    val severity: ModuleValidationSeverity,
    val message: String,
)

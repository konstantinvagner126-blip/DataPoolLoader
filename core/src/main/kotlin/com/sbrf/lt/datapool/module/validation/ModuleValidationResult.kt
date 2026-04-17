package com.sbrf.lt.datapool.module.validation

/**
 * Результат проверки модуля: итоговый статус и список проблем.
 */
data class ModuleValidationResult(
    val status: ModuleValidationStatus,
    val issues: List<ModuleValidationIssue> = emptyList(),
)

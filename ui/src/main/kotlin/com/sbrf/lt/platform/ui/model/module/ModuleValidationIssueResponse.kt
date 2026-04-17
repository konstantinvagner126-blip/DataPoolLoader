package com.sbrf.lt.platform.ui.model

/**
 * Упрощенное представление одной проблемы валидации модуля для UI.
 */
data class ModuleValidationIssueResponse(
    val severity: String,
    val message: String,
)

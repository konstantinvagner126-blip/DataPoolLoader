package com.sbrf.lt.platform.ui.model

/**
 * Краткая карточка модуля в каталоге UI.
 */
data class ModuleCatalogItemResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
    val hasActiveRun: Boolean = false,
)

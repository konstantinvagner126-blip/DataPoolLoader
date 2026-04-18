package com.sbrf.lt.platform.ui.model

/**
 * Краткая карточка модуля в каталоге UI.
 */
data class ModuleCatalogItemResponse(
    val id: String,
    val descriptor: ModuleMetadataDescriptorResponse,
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
    val hasActiveRun: Boolean = false,
) {
    val title: String
        get() = descriptor.title

    val description: String?
        get() = descriptor.description

    val tags: List<String>
        get() = descriptor.tags

    val hiddenFromUi: Boolean
        get() = descriptor.hiddenFromUi
}

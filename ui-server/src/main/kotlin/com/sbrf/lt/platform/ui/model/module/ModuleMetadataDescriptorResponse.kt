package com.sbrf.lt.platform.ui.model

/**
 * Устойчивый metadata-контракт модуля, общий для каталога и editor/session DTO.
 */
data class ModuleMetadataDescriptorResponse(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

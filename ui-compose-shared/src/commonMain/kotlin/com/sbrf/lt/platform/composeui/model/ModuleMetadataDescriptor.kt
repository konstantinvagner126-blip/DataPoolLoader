package com.sbrf.lt.platform.composeui.model

import kotlinx.serialization.Serializable

@Serializable
data class ModuleMetadataDescriptor(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

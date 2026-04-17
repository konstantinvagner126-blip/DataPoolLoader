package com.sbrf.lt.datapool.db.registry.model

/**
 * Черновик DB-модуля, который нужно создать в registry.
 */
data class RegistryModuleDraft(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val configText: String,
    val sqlFiles: Map<String, String> = emptyMap(),
    val hiddenFromUi: Boolean = true,
)

package com.sbrf.lt.platform.ui.model

/**
 * Команда сохранения модуля из editor shell.
 */
data class SaveModuleRequest(
    val configText: String,
    val sqlFiles: Map<String, String>,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

package com.sbrf.lt.platform.ui.model

/**
 * Команда создания нового DB-модуля через UI.
 */
data class CreateDbModuleRequest(
    val moduleCode: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val configText: String = "",
    val hiddenFromUi: Boolean = true,
)

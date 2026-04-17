package com.sbrf.lt.platform.ui.sync

/**
 * Метаданные UI-модуля, прочитанные из `ui-module.yml` перед импортом в DB-режим.
 */
internal data class ModuleUiMetadata(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

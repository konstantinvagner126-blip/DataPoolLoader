package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ModuleFileContent

/**
 * Снимок личного черновика DB-модуля, сохраняемый в `working_copy_json`.
 */
data class WorkingCopySnapshot(
    val configText: String = "",
    val sqlFiles: List<ModuleFileContent> = emptyList(),
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
)

package com.sbrf.lt.platform.ui.model

import java.nio.file.Path

/**
 * Полное описание файлового UI-модуля с путями к конфигу и ресурсам.
 */
data class ModuleDescriptor(
    val id: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
    val validationStatus: String = "VALID",
    val validationIssues: List<ModuleValidationIssueResponse> = emptyList(),
    val configFile: Path,
    val resourcesDir: Path,
)

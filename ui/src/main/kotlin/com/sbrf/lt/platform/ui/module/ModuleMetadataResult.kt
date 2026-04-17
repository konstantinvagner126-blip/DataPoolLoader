package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse

/**
 * Метаданные файлового модуля из `ui-module.yml`, дополненные предупреждением о проблеме чтения.
 */
internal data class ModuleMetadataResult(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val hiddenFromUi: Boolean = false,
    val issue: ModuleValidationIssueResponse? = null,
)

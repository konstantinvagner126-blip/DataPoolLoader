package com.sbrf.lt.platform.ui.model

/**
 * Агрегированная сводка по валидности модулей в каталоге.
 */
data class ModuleCatalogDiagnosticsResponse(
    val totalModules: Int = 0,
    val validModules: Int = 0,
    val warningModules: Int = 0,
    val invalidModules: Int = 0,
    val totalIssues: Int = 0,
)

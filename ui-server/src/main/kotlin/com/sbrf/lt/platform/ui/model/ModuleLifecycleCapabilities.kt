package com.sbrf.lt.platform.ui.model

/**
 * Набор доступных lifecycle-операций редактора модуля для текущего storage mode.
 */
data class ModuleLifecycleCapabilities(
    val save: Boolean = false,
    val saveWorkingCopy: Boolean = false,
    val discardWorkingCopy: Boolean = false,
    val publish: Boolean = false,
    val run: Boolean = false,
    val createModule: Boolean = false,
    val deleteModule: Boolean = false,
)

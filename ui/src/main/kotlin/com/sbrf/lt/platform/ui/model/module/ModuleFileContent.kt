package com.sbrf.lt.platform.ui.model

/**
 * Содержимое одного SQL-ресурса модуля, доступного в редакторе.
 */
data class ModuleFileContent(
    val label: String,
    val path: String,
    val content: String,
    val exists: Boolean,
)

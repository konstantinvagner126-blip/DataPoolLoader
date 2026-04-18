package com.sbrf.lt.platform.ui.model

/**
 * Заголовочные данные общего экрана истории запусков.
 */
data class ModuleRunPageSessionResponse(
    val storageMode: String,
    val moduleId: String,
    val moduleTitle: String,
    val moduleMeta: String,
)

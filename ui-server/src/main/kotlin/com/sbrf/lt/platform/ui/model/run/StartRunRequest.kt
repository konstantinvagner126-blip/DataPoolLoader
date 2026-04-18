package com.sbrf.lt.platform.ui.model

/**
 * Команда запуска файлового модуля из UI.
 */
data class StartRunRequest(
    val moduleId: String,
    val configText: String,
    val sqlFiles: Map<String, String>,
)

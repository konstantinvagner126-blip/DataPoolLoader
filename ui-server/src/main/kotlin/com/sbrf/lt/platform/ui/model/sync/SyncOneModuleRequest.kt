package com.sbrf.lt.platform.ui.model

/**
 * Команда точечного импорта одного файлового модуля в DB registry.
 */
data class SyncOneModuleRequest(
    val moduleCode: String,
)

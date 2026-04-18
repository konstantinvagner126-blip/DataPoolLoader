package com.sbrf.lt.platform.ui.model

/**
 * Команда выборочной синхронизации нескольких файловых модулей в DB registry.
 */
data class SyncSelectedModulesRequest(
    val moduleCodes: List<String>,
)

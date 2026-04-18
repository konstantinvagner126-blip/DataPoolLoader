package com.sbrf.lt.platform.ui.model

/**
 * Команда запуска cleanup истории DB-запусков.
 */
data class DatabaseRunHistoryCleanupRequest(
    val disableSafeguard: Boolean = false,
)

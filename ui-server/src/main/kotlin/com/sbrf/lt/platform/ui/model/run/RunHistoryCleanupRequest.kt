package com.sbrf.lt.platform.ui.model

/**
 * Request для cleanup истории запусков текущего режима UI.
 */
data class RunHistoryCleanupRequest(
    val disableSafeguard: Boolean = false,
)

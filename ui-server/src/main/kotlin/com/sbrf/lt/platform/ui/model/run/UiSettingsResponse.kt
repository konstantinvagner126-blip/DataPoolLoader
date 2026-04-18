package com.sbrf.lt.platform.ui.model

/**
 * Пользовательские настройки отображения запуска в UI.
 */
data class UiSettingsResponse(
    val showTechnicalDiagnostics: Boolean = true,
    val showRawSummaryJson: Boolean = false,
)

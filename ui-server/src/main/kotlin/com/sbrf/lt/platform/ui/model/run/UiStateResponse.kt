package com.sbrf.lt.platform.ui.model

/**
 * Полное состояние файлового режима UI: credentials, активный запуск и история.
 */
data class UiStateResponse(
    val credentialsStatus: CredentialsStatusResponse,
    val uiSettings: UiSettingsResponse = UiSettingsResponse(),
    val activeRun: UiRunSnapshot? = null,
    val history: List<UiRunSnapshot> = emptyList(),
)

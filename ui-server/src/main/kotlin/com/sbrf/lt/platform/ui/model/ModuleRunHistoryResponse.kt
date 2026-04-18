package com.sbrf.lt.platform.ui.model

/**
 * Список запусков выбранного модуля в едином контракте для FILES и DATABASE.
 */
data class ModuleRunHistoryResponse(
    val storageMode: String,
    val moduleId: String,
    val activeRunId: String? = null,
    val uiSettings: UiSettingsResponse = UiSettingsResponse(),
    val runs: List<ModuleRunSummaryResponse> = emptyList(),
)

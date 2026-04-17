package com.sbrf.lt.platform.ui.model

/**
 * Полная карточка запуска модуля в едином контракте для FILES и DATABASE.
 */
data class ModuleRunDetailsResponse(
    val run: ModuleRunSummaryResponse,
    val summaryJson: String? = null,
    val sourceResults: List<ModuleRunSourceResultResponse> = emptyList(),
    val events: List<ModuleRunEventResponse> = emptyList(),
    val artifacts: List<ModuleRunArtifactResponse> = emptyList(),
)

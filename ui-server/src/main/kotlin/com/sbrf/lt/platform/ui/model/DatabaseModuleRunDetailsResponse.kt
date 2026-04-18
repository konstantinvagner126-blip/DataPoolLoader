package com.sbrf.lt.platform.ui.model

/**
 * Полная карточка одного DB-запуска для детального просмотра в UI.
 */
data class DatabaseModuleRunDetailsResponse(
    val run: DatabaseModuleRunSummaryResponse,
    val summaryJson: String,
    val sourceResults: List<DatabaseRunSourceResultResponse>,
    val events: List<DatabaseRunEventResponse>,
    val artifacts: List<DatabaseRunArtifactResponse>,
)

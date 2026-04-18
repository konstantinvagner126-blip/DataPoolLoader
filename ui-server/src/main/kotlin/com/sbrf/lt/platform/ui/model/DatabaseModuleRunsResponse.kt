package com.sbrf.lt.platform.ui.model

/**
 * Список запусков выбранного DB-модуля.
 */
data class DatabaseModuleRunsResponse(
    val moduleCode: String,
    val runs: List<DatabaseModuleRunSummaryResponse>,
)

package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.platform.ui.config.UiRuntimeContext

/**
 * Полная карточка DB-модуля с информацией о ревизии и личном черновике.
 */
data class DatabaseModuleDetailsResponse(
    val runtimeContext: UiRuntimeContext,
    val module: ModuleDetailsResponse,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String? = null,
    val workingCopyStatus: String? = null,
    val baseRevisionId: String? = null,
)

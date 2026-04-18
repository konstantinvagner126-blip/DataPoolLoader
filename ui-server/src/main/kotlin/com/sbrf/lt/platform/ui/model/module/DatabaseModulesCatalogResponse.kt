package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.platform.ui.config.UiRuntimeContext

/**
 * Ответ API с каталогом DB-модулей и runtime-контекстом режима UI.
 */
data class DatabaseModulesCatalogResponse(
    val runtimeContext: UiRuntimeContext,
    val diagnostics: ModuleCatalogDiagnosticsResponse = ModuleCatalogDiagnosticsResponse(),
    val modules: List<ModuleCatalogItemResponse>,
)

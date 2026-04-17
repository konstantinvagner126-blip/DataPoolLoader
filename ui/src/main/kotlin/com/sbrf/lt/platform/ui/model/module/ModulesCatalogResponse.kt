package com.sbrf.lt.platform.ui.model

/**
 * Ответ API с каталогом файловых модулей и диагностикой `appsRoot`.
 */
data class ModulesCatalogResponse(
    val appsRootStatus: AppsRootStatusResponse,
    val diagnostics: ModuleCatalogDiagnosticsResponse = ModuleCatalogDiagnosticsResponse(),
    val modules: List<ModuleCatalogItemResponse>,
)

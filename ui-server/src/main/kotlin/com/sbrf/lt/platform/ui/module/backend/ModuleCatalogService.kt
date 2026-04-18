package com.sbrf.lt.platform.ui.module.backend

import com.sbrf.lt.platform.ui.model.AppsRootStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse

/**
 * Каталог модулей для конкретного storage mode.
 */
interface ModuleCatalogService {
    fun listModules(includeHidden: Boolean = false): List<ModuleCatalogItemResponse>

    fun catalogStatus(): AppsRootStatusResponse? = null
}

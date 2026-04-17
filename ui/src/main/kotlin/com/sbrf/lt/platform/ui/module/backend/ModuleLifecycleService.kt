package com.sbrf.lt.platform.ui.module.backend

import com.sbrf.lt.platform.ui.model.ModuleLifecycleCapabilities

/**
 * Lifecycle-операции storage mode. Unsupported сценарии выражаются capabilities.
 */
interface ModuleLifecycleService {
    fun capabilities(): ModuleLifecycleCapabilities
}

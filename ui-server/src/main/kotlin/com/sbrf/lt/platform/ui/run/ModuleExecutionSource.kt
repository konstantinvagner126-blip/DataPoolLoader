package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.StartRunRequest

/**
 * Готовит runtime snapshot модуля для запуска из конкретного источника хранения.
 */
fun interface ModuleExecutionSource {
    fun prepareExecution(
        module: ModuleDescriptor,
        request: StartRunRequest,
    ): RuntimeModuleSnapshot
}

package com.sbrf.lt.platform.ui.module.backend

import com.sbrf.lt.platform.ui.model.ModuleEditorSessionResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest

/**
 * Единый контракт загрузки и сохранения редактируемого модуля.
 */
interface ModuleEditorStore {
    fun loadModule(moduleId: String, actor: ModuleActor? = null): ModuleEditorSessionResponse

    fun saveModule(moduleId: String, request: SaveModuleRequest, actor: ModuleActor? = null)
}

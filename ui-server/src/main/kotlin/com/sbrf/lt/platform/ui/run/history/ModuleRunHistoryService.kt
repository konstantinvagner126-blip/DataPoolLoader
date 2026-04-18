package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleRunHistoryResponse
import com.sbrf.lt.platform.ui.model.ModuleRunPageSessionResponse
import com.sbrf.lt.platform.ui.module.backend.ModuleActor

/**
 * Общий backend-контракт экрана истории запусков для FILES и DATABASE.
 */
interface ModuleRunHistoryService {
    fun loadSession(moduleId: String, actor: ModuleActor? = null): ModuleRunPageSessionResponse
    fun listRuns(moduleId: String, actor: ModuleActor? = null, limit: Int = 20): ModuleRunHistoryResponse
    fun loadRunDetails(moduleId: String, runId: String, actor: ModuleActor? = null): ModuleRunDetailsResponse
}

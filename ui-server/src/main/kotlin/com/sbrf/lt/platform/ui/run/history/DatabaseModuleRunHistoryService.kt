package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import com.sbrf.lt.platform.ui.model.ModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleRunEventResponse
import com.sbrf.lt.platform.ui.model.ModuleRunHistoryResponse
import com.sbrf.lt.platform.ui.model.ModuleRunPageSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations

/**
 * DATABASE-реализация общего backend-контракта истории запусков.
 */
class DatabaseModuleRunHistoryService(
    private val moduleBackend: DatabaseModuleBackend,
    private val runService: DatabaseModuleRunOperations,
) : ModuleRunHistoryService {
    override fun loadSession(moduleId: String, actor: ModuleActor?): ModuleRunPageSessionResponse {
        requireNotNull(actor) { "Для DATABASE storage нужен actor." }
        val session = moduleBackend.loadModule(moduleId, actor)
        return buildDatabaseModuleRunPageSession(session)
    }

    override fun listRuns(moduleId: String, actor: ModuleActor?, limit: Int): ModuleRunHistoryResponse {
        val response = runService.listRuns(moduleId, limit)
        return buildDatabaseModuleRunHistoryResponse(moduleId, response)
    }

    override fun loadRunDetails(moduleId: String, runId: String, actor: ModuleActor?): ModuleRunDetailsResponse {
        val response = runService.loadRunDetails(moduleId, runId)
        return buildDatabaseModuleRunDetailsResponse(response)
    }
}

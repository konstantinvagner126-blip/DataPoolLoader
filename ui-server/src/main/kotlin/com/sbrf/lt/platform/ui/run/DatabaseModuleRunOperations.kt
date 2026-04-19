package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunStartResponse

/**
 * Контракт DB-run операций для server/history слоя.
 */
interface DatabaseModuleRunOperations {
    fun startRun(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): DatabaseRunStartResponse

    fun listRuns(moduleCode: String, limit: Int = 20): DatabaseModuleRunsResponse

    fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse

    fun activeModuleCodes(): Set<String>
}

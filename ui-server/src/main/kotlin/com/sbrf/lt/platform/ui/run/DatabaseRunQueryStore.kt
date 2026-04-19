package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse

/**
 * Контракт чтения DB run-history.
 */
interface DatabaseRunQueryStore {
    fun activeModuleCodes(): Set<String>

    fun listRuns(moduleCode: String, limit: Int = 20): List<DatabaseModuleRunSummaryResponse>

    fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse
}

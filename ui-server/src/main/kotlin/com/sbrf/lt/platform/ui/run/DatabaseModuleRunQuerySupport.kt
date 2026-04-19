package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse

internal class DatabaseModuleRunQuerySupport(
    private val runQueryStore: DatabaseRunQueryStore,
    private val activeRunRegistry: DatabaseModuleActiveRunRegistry,
) {
    fun listRuns(moduleCode: String, limit: Int): DatabaseModuleRunsResponse =
        DatabaseModuleRunsResponse(
            moduleCode = moduleCode,
            runs = runQueryStore.listRuns(moduleCode, limit),
        )

    fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse =
        runQueryStore.loadRunDetails(moduleCode, runId)

    fun activeModuleCodes(): Set<String> =
        buildSet {
            addAll(runQueryStore.activeModuleCodes())
            addAll(activeRunRegistry.activeModuleCodes())
        }
}

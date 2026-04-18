package com.sbrf.lt.platform.composeui.module_runs

interface ModuleRunsApi {
    suspend fun loadSession(storage: String, moduleId: String): ModuleRunPageSessionResponse

    suspend fun loadHistory(storage: String, moduleId: String, limit: Int = 20): ModuleRunHistoryResponse

    suspend fun loadRunDetails(storage: String, moduleId: String, runId: String): ModuleRunDetailsResponse
}

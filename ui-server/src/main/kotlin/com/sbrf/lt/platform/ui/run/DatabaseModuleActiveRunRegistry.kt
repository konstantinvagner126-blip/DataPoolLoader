package com.sbrf.lt.platform.ui.run

import java.util.concurrent.ConcurrentHashMap

class DatabaseModuleActiveRunRegistry {
    private val activeRunIdsByModule = ConcurrentHashMap<String, String>()

    fun currentRunId(moduleCode: String): String? = activeRunIdsByModule[moduleCode]

    fun markActive(moduleCode: String, runId: String) {
        activeRunIdsByModule[moduleCode] = runId
    }

    fun clear(moduleCode: String, runId: String) {
        activeRunIdsByModule.remove(moduleCode, runId)
    }

    fun activeModuleCodes(): Set<String> = activeRunIdsByModule.keys
}

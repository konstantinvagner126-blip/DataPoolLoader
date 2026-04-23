package com.sbrf.lt.platform.composeui.module_sync

internal interface ModuleSyncLoadStore {
    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        selectiveSyncVisible: Boolean = false,
        selectedModuleCodes: Set<String> = emptySet(),
        moduleSearchQuery: String = "",
    ): ModuleSyncPageState
}

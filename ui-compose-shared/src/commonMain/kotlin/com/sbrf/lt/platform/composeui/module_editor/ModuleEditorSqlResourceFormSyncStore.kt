package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorSqlResourceFormSyncStore {
    fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String>

    suspend fun applySqlResourceRename(
        current: ModuleEditorPageState,
        currentPath: String,
        nextPath: String,
    ): ModuleEditorPageState
}

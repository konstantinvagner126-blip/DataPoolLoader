package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceMutationSupport(
    formSyncStore: ModuleEditorSqlResourceFormSyncStore,
    namingSupport: ModuleEditorStoreSqlResourceNamingSupport,
) {
    private val draftMutationSupport = ModuleEditorStoreSqlResourceDraftMutationSupport(formSyncStore, namingSupport)
    private val renameSupport = ModuleEditorStoreSqlResourceRenameSupport(formSyncStore, namingSupport)

    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState =
        draftMutationSupport.createSqlResource(current, rawName)

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState =
        renameSupport.renameSqlResource(current, rawName)

    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState =
        draftMutationSupport.deleteSqlResource(current)
}

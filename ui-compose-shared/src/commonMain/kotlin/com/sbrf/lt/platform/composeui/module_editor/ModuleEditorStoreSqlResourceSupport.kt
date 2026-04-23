package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSqlResourceSupport(
    private val mutationSupport: ModuleEditorStoreSqlResourceMutationSupport,
) {
    constructor(
        configFormSupport: ModuleEditorStoreConfigFormSupport,
        namingSupport: ModuleEditorStoreSqlResourceNamingSupport = ModuleEditorStoreSqlResourceNamingSupport(),
    ) : this(
        mutationSupport = ModuleEditorStoreSqlResourceMutationSupport(
            formSyncStore = configFormSupport,
            namingSupport = namingSupport,
        ),
    )

    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState =
        mutationSupport.createSqlResource(current, rawName)

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState =
        mutationSupport.renameSqlResource(current, rawName)

    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState =
        mutationSupport.deleteSqlResource(current)
}

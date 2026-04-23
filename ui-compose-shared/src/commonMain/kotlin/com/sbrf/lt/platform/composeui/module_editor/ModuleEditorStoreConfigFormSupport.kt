package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreConfigFormSupport(
    private val mutationSupport: ModuleEditorStoreConfigFormMutationSupport,
    private val sqlResourceSupport: ModuleEditorStoreConfigFormSqlResourceSupport,
) {
    constructor(api: ModuleEditorApi) : this(
        mutationSupport = ModuleEditorStoreConfigFormMutationSupport(api),
        sqlResourceSupport = ModuleEditorStoreConfigFormSqlResourceSupport(),
    )

    suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState =
        mutationSupport.syncConfigFormFromConfigDraft(current)

    suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        mutationSupport.applyConfigForm(current, formState)

    fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState =
        mutationSupport.startConfigFormSync(current)

    fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String> =
        sqlResourceSupport.buildSqlResourceUsages(formState, path)

    suspend fun applySqlResourceRename(
        current: ModuleEditorPageState,
        currentPath: String,
        nextPath: String,
    ): ModuleEditorPageState {
        val formState = current.configFormState ?: return current
        if (!sqlResourceSupport.sqlResourceUsedByForm(formState, currentPath)) {
            return current
        }
        val updatedFormState = sqlResourceSupport.renameSqlResourceInFormState(formState, currentPath, nextPath)
        return applyConfigForm(
            current.copy(
                configFormState = updatedFormState,
                configFormLoading = false,
                configFormError = null,
            ),
            updatedFormState,
        )
    }
}

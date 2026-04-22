package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreConfigFormSupport(
    private val api: ModuleEditorApi,
) {
    suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState {
        if (current.configTextDraft.isBlank()) {
            return current.copy(
                configFormState = null,
                configFormError = "application.yml пустой. Восстанови конфиг или открой YAML-вкладку.",
                configFormLoading = false,
                configFormSourceText = "",
            )
        }
        return runCatching {
            val formState = api.parseConfigForm(current.configTextDraft)
            current.copy(
                configFormState = formState,
                configFormError = null,
                configFormLoading = false,
                configFormSourceText = current.configTextDraft,
            )
        }.getOrElse { error ->
            current.copy(
                configFormLoading = false,
                configFormError = error.message ?: "Не удалось разобрать application.yml для визуальной формы.",
            )
        }
    }

    suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        runCatching {
            val response = api.applyConfigForm(current.configTextDraft, formState)
            current.copy(
                configTextDraft = response.configText,
                configFormState = response.formState,
                configFormError = null,
                configFormLoading = false,
                configFormSourceText = response.configText,
            )
        }.getOrElse { error ->
            current.copy(
                configFormError = error.message ?: "Не удалось применить изменения формы к application.yml.",
                configFormLoading = false,
            )
        }

    fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(configFormLoading = true, configFormError = null)

    fun buildSqlResourceUsages(
        formState: ConfigFormStateDto?,
        path: String,
    ): List<String> {
        if (formState == null) {
            return emptyList()
        }
        return buildList {
            if (formState.commonSqlFile == path) {
                add("SQL по умолчанию")
            }
            formState.sources.forEach { source ->
                if (source.sqlFile == path) {
                    add("Источник: ${source.name}")
                }
            }
        }
    }

    suspend fun applySqlResourceRename(
        current: ModuleEditorPageState,
        currentPath: String,
        nextPath: String,
    ): ModuleEditorPageState {
        val formState = current.configFormState ?: return current
        if (!sqlResourceUsedByForm(formState, currentPath)) {
            return current
        }
        val updatedFormState = renameSqlResourceInFormState(formState, currentPath, nextPath)
        return applyConfigForm(
            current.copy(
                configFormState = updatedFormState,
                configFormLoading = false,
                configFormError = null,
            ),
            updatedFormState,
        )
    }

    private fun sqlResourceUsedByForm(
        formState: ConfigFormStateDto,
        path: String,
    ): Boolean =
        formState.commonSqlFile == path || formState.sources.any { it.sqlFile == path }

    private fun renameSqlResourceInFormState(
        formState: ConfigFormStateDto,
        currentPath: String,
        nextPath: String,
    ): ConfigFormStateDto =
        formState.copy(
            commonSqlFile = formState.commonSqlFile
                ?.takeUnless { it == currentPath }
                ?: nextPath.takeIf { formState.commonSqlFile == currentPath },
            sources = formState.sources.map { source ->
                if (source.sqlFile == currentPath) {
                    source.copy(sqlFile = nextPath)
                } else {
                    source
                }
            },
        )
}

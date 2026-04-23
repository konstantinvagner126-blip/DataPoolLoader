package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreConfigFormMutationSupport(
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
}

package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun ModuleEditorSettingsForm(
    storageMode: String,
    state: ModuleEditorPageState,
    module: ModuleDetailsResponse,
    onRefreshFromConfig: () -> Unit,
    onApplyFormState: (ConfigFormStateDto) -> Unit,
) {
    val configFormError = state.configFormError
    if (state.configFormLoading && state.configFormState == null) {
        LoadingStateCard(
            title = "Настройки модуля",
            text = "Собираю визуальную форму из application.yml.",
        )
        return
    }

    val formState = state.configFormState
    if (formState == null) {
        SectionCard(
            title = "Настройки модуля",
            subtitle = "Форма строится из application.yml через тот же backend-контракт, что и в основном UI.",
            actions = {
                ConfigFormActionButton(
                    label = "Собрать форму заново",
                    toneClass = "btn-outline-secondary",
                    disabled = state.configFormLoading,
                    onClick = onRefreshFromConfig,
                )
            },
        ) {
            if (!configFormError.isNullOrBlank()) {
                AlertBanner(configFormError, "warning")
            }
            P({ classes("text-secondary", "mb-0") }) {
                Text("Сейчас визуальная форма недоступна. Исправь YAML или пересобери форму повторно.")
            }
        }
        return
    }

    val sqlResources = module.sqlFiles.map {
        SqlResourceOption(
            label = it.label.ifBlank { it.path },
            path = it.path,
            exists = it.exists,
        )
    }
    val sectionStateKeyPrefix = remember(storageMode, module.id) {
        "module-editor.config-sections.$storageMode.${module.id}"
    }

    SectionCard(
        title = "Настройки модуля",
        subtitle = "Compose-форма использует те же parse/apply endpoint-ы, что и production UI.",
        actions = {
            if (state.configFormNeedsSync) {
                ConfigFormActionButton(
                    label = "Перечитать из application.yml",
                    toneClass = "btn-outline-warning",
                    disabled = state.configFormLoading,
                    onClick = onRefreshFromConfig,
                )
            }
        },
    ) {
        if (!configFormError.isNullOrBlank()) {
            AlertBanner(configFormError, "warning")
        }
        if (state.configFormNeedsSync) {
            AlertBanner(
                "application.yml менялся вручную. Перед редактированием формы лучше перечитать YAML и синхронизировать состояние.",
                "warning",
            )
        }
        if (formState.warnings.isNotEmpty()) {
            AlertBanner(formState.warnings.joinToString(" "), "warning")
        }

        GeneralSettingsSection(
            formState = formState,
            sectionStateKey = "$sectionStateKeyPrefix.general",
            disabled = state.configFormLoading,
            onCommit = onApplyFormState,
        )
        DefaultSqlSection(
            formState = formState,
            sqlResources = sqlResources,
            storageMode = storageMode,
            sectionStateKey = "$sectionStateKeyPrefix.default-sql",
            disabled = state.configFormLoading,
            onCommit = onApplyFormState,
        )
        SourcesSection(
            formState = formState,
            sqlResources = sqlResources,
            storageMode = storageMode,
            sectionStateKey = "$sectionStateKeyPrefix.sources",
            disabled = state.configFormLoading,
            onCommit = onApplyFormState,
        )
        QuotasSection(
            formState = formState,
            sectionStateKey = "$sectionStateKeyPrefix.quotas",
            disabled = state.configFormLoading,
            onCommit = onApplyFormState,
        )
        TargetSection(
            formState = formState,
            sectionStateKey = "$sectionStateKeyPrefix.target",
            disabled = state.configFormLoading,
            onCommit = onApplyFormState,
        )
    }
}

@Composable
private fun ConfigFormActionButton(
    label: String,
    toneClass: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass)
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

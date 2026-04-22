package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun QuotasSection(
    formState: ConfigFormStateDto,
    sectionStateKey: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    ConfigSectionCard(
        title = "Квоты",
        subtitle = "Используются в режиме quota.",
        sectionStateKey = sectionStateKey,
        actions = {
            ConfigCollectionActionButton(
                label = "Добавить квоту",
                toneClass = "btn-outline-secondary",
                disabled = disabled,
            ) {
                onCommit(
                    formState.copy(
                        quotas = formState.quotas + ConfigFormQuotaStateDto(source = "", percent = null),
                    ),
                )
            }
        },
    ) {
        if (formState.quotas.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) { Text("Квоты не заданы.") }
        }
        formState.quotas.forEachIndexed { index, quota ->
            ConfigCollectionCard(
                title = quota.source.ifBlank { "Квота ${index + 1}" },
                removeLabel = "Удалить",
                disabled = disabled,
                onRemove = {
                    onCommit(formState.copy(quotas = formState.quotas.filterIndexed { quotaIndex, _ -> quotaIndex != index }))
                },
            ) {
                CommitTextField(
                    label = "Источник",
                    value = quota.source,
                    disabled = disabled,
                    helpText = "Код источника из списка app.sources.",
                ) { onCommit(updateQuota(formState, index) { copy(source = it) }) }
                CommitOptionalDoubleField(
                    label = "Процент",
                    value = quota.percent,
                    disabled = disabled,
                    helpText = "Можно оставить пустым.",
                ) { onCommit(updateQuota(formState, index) { copy(percent = it) }) }
            }
        }
    }
}

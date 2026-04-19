package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SourcesSection(
    formState: ConfigFormStateDto,
    sqlResources: List<SqlResourceOption>,
    storageMode: String,
    sectionStateKey: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    ConfigSectionCard(
        title = "Источники",
        subtitle = "Подключения и SQL по каждому источнику.",
        sectionStateKey = sectionStateKey,
        defaultExpanded = false,
        actions = {
            ConfigCollectionActionButton(
                label = "Добавить источник",
                toneClass = "btn-outline-secondary",
                disabled = disabled,
            ) {
                onCommit(
                    formState.copy(
                        sources = formState.sources + ConfigFormSourceStateDto(
                            name = "",
                            jdbcUrl = "",
                            username = "",
                            password = "",
                        ),
                    ),
                )
            }
        },
    ) {
        if (formState.sources.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) {
                Text("Источники не заданы.")
            }
        }
        formState.sources.forEachIndexed { index, source ->
            val sourceSqlState = buildSourceSqlState(source, sqlResources)
            ConfigCollectionCard(
                title = source.name.ifBlank { "Источник ${index + 1}" },
                note = source.jdbcUrl.ifBlank { null },
                removeLabel = "Удалить",
                disabled = disabled,
                onRemove = {
                    onCommit(formState.copy(sources = formState.sources.filterIndexed { sourceIndex, _ -> sourceIndex != index }))
                },
            ) {
                CommitTextField(
                    label = "Название",
                    value = source.name,
                    disabled = disabled,
                    helpText = "Идентификатор источника в конфиге.",
                ) { onCommit(updateSource(formState, index) { copy(name = it) }) }
                CommitTextField(
                    label = "JDBC URL",
                    value = source.jdbcUrl,
                    disabled = disabled,
                    helpText = "Поддерживаются placeholders из credential.properties.",
                ) { onCommit(updateSource(formState, index) { copy(jdbcUrl = it) }) }
                CommitTextField(
                    label = "Пользователь",
                    value = source.username,
                    disabled = disabled,
                    helpText = "Можно использовать placeholders.",
                ) { onCommit(updateSource(formState, index) { copy(username = it) }) }
                CommitTextField(
                    label = "Пароль",
                    value = source.password,
                    disabled = disabled,
                    helpText = "Можно использовать placeholders.",
                ) { onCommit(updateSource(formState, index) { copy(password = it) }) }
                CommitSelectField(
                    label = "Источник SQL",
                    value = sourceSqlState.mode,
                    options = buildSourceSqlModeOptions(sourceSqlState),
                    disabled = disabled,
                    helpText = sourceSqlState.summary,
                ) { mode ->
                    onCommit(applySourceSqlMode(formState, index, mode, sqlResources))
                }
                CommitSqlModeFields(
                    mode = sourceSqlState.mode,
                    inlineText = sourceSqlState.inlineText,
                    catalogPath = sourceSqlState.catalogPath,
                    externalRef = sourceSqlState.externalRef,
                    sqlResources = sqlResources,
                    disabled = disabled,
                    storageMode = storageMode,
                    inlineRowsCount = 5,
                    inlineHelpText = "Если задан, перекрывает SQL по умолчанию.",
                    onInlineCommit = { onCommit(updateSource(formState, index) { copy(sql = it, sqlFile = null) }) },
                    onCatalogCommit = { onCommit(updateSource(formState, index) { copy(sql = null, sqlFile = it.ifBlank { null }) }) },
                )
            }
        }
    }
}

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

@Composable
private fun ConfigCollectionCard(
    title: String,
    note: String? = null,
    removeLabel: String,
    disabled: Boolean,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    Div({ classes("config-form-card", "mb-3") }) {
        Div({ classes("config-form-card-body") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-center", "mb-3", "gap-2") }) {
                Div {
                    Div({ classes("config-form-card-title") }) {
                        Text(title)
                    }
                    if (!note.isNullOrBlank()) {
                        Div({ classes("config-form-help") }) {
                            Text(note)
                        }
                    }
                }
                ConfigCollectionActionButton(
                    label = removeLabel,
                    toneClass = "btn-outline-danger",
                    disabled = disabled,
                ) {
                    onRemove()
                }
            }
            Div({ classes("config-form-fields") }) {
                content()
            }
        }
    }
}

@Composable
private fun ConfigCollectionActionButton(
    label: String,
    toneClass: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "btn-sm")
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

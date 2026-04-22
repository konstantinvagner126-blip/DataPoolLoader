package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
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
            ModuleEditorSourceConfigCard(
                formState = formState,
                source = source,
                sourceIndex = index,
                sqlResources = sqlResources,
                storageMode = storageMode,
                disabled = disabled,
                onCommit = onCommit,
            )
        }
    }
}

@Composable
private fun ModuleEditorSourceConfigCard(
    formState: ConfigFormStateDto,
    source: ConfigFormSourceStateDto,
    sourceIndex: Int,
    sqlResources: List<SqlResourceOption>,
    storageMode: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    val sourceSqlState = buildSourceSqlState(source, sqlResources)
    ConfigCollectionCard(
        title = source.name.ifBlank { "Источник ${sourceIndex + 1}" },
        note = source.jdbcUrl.ifBlank { null },
        removeLabel = "Удалить",
        disabled = disabled,
        onRemove = {
            onCommit(formState.copy(sources = formState.sources.filterIndexed { index, _ -> index != sourceIndex }))
        },
    ) {
        CommitTextField(
            label = "Название",
            value = source.name,
            disabled = disabled,
            helpText = "Идентификатор источника в конфиге.",
        ) { onCommit(updateSource(formState, sourceIndex) { copy(name = it) }) }
        CommitTextField(
            label = "JDBC URL",
            value = source.jdbcUrl,
            disabled = disabled,
            helpText = "Поддерживаются placeholders из credential.properties.",
        ) { onCommit(updateSource(formState, sourceIndex) { copy(jdbcUrl = it) }) }
        CommitTextField(
            label = "Пользователь",
            value = source.username,
            disabled = disabled,
            helpText = "Можно использовать placeholders.",
        ) { onCommit(updateSource(formState, sourceIndex) { copy(username = it) }) }
        CommitTextField(
            label = "Пароль",
            value = source.password,
            disabled = disabled,
            helpText = "Можно использовать placeholders.",
        ) { onCommit(updateSource(formState, sourceIndex) { copy(password = it) }) }
        CommitSelectField(
            label = "Источник SQL",
            value = sourceSqlState.mode,
            options = buildSourceSqlModeOptions(sourceSqlState),
            disabled = disabled,
            helpText = sourceSqlState.summary,
        ) { mode ->
            onCommit(applySourceSqlMode(formState, sourceIndex, mode, sqlResources))
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
            onInlineCommit = { onCommit(updateSource(formState, sourceIndex) { copy(sql = it, sqlFile = null) }) },
            onCatalogCommit = { onCommit(updateSource(formState, sourceIndex) { copy(sql = null, sqlFile = it.ifBlank { null }) }) },
        )
    }
}

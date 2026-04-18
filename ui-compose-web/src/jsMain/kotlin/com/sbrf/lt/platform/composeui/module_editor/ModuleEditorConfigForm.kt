package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

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
                Button(attrs = {
                    classes("btn", "btn-outline-secondary")
                    attr("type", "button")
                    if (state.configFormLoading) {
                        disabled()
                    }
                    onClick { onRefreshFromConfig() }
                }) {
                    Text("Собрать форму заново")
                }
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
                Button(attrs = {
                    classes("btn", "btn-outline-warning")
                    attr("type", "button")
                    if (state.configFormLoading) {
                        disabled()
                    }
                    onClick { onRefreshFromConfig() }
                }) {
                    Text("Перечитать из application.yml")
                }
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
private fun GeneralSettingsSection(
    formState: ConfigFormStateDto,
    sectionStateKey: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    ConfigSectionCard(
        title = "Общие настройки",
        subtitle = "Базовые параметры запуска и формирования выходных файлов.",
        sectionStateKey = sectionStateKey,
    ) {
        Div({ classes("config-form-fields") }) {
            CommitTextField(
                label = "Каталог output",
                value = formState.outputDir,
                disabled = disabled,
                helpText = "Обычно оставляем относительный путь ./output.",
            ) { onCommit(formState.copy(outputDir = it)) }
            CommitSelectField(
                label = "Формат файла",
                value = formState.fileFormat,
                options = listOf("csv" to "csv"),
                disabled = disabled,
                helpText = "Сейчас поддерживается только CSV.",
            ) { onCommit(formState.copy(fileFormat = it)) }
            CommitSelectField(
                label = "Режим объединения",
                value = formState.mergeMode,
                options = listOf(
                    "plain" to "Прямое объединение",
                    "round_robin" to "По очереди",
                    "proportional" to "Пропорционально",
                    "quota" to "По квотам",
                ),
                disabled = disabled,
                helpText = "Как объединять данные из источников.",
            ) { onCommit(formState.copy(mergeMode = it)) }
            CommitSelectField(
                label = "Режим ошибок",
                value = formState.errorMode,
                options = listOf("continue_on_error" to "Продолжать при ошибке"),
                disabled = disabled,
                helpText = "Сейчас поддерживается продолжение обработки при ошибке источника.",
            ) { onCommit(formState.copy(errorMode = it)) }
            CommitIntField(
                label = "Параллелизм",
                value = formState.parallelism,
                disabled = disabled,
                helpText = "Сколько источников обрабатывать одновременно.",
            ) { onCommit(formState.copy(parallelism = it)) }
            CommitIntField(
                label = "Размер JDBC-пакета",
                value = formState.fetchSize,
                disabled = disabled,
                helpText = "Размер порции данных при чтении результата.",
            ) { onCommit(formState.copy(fetchSize = it)) }
            CommitOptionalIntField(
                label = "Таймаут запроса, сек",
                value = formState.queryTimeoutSec,
                disabled = disabled,
                helpText = "Можно оставить пустым.",
            ) { onCommit(formState.copy(queryTimeoutSec = it)) }
            CommitLongField(
                label = "Логировать каждые N строк",
                value = formState.progressLogEveryRows,
                disabled = disabled,
                helpText = "Как часто логировать ход выгрузки.",
            ) { onCommit(formState.copy(progressLogEveryRows = it)) }
            CommitOptionalLongField(
                label = "Максимум строк в merged",
                value = formState.maxMergedRows,
                disabled = disabled,
                helpText = "Можно оставить пустым.",
            ) { onCommit(formState.copy(maxMergedRows = it)) }
        }
        CommitCheckboxField(
            label = "Удалять output-файлы после завершения",
            checked = formState.deleteOutputFilesAfterCompletion,
            disabled = disabled,
            helpText = "Если включено, итоговые CSV и summary удаляются после завершения запуска.",
        ) {
            onCommit(formState.copy(deleteOutputFilesAfterCompletion = it))
        }
    }
}

@Composable
private fun DefaultSqlSection(
    formState: ConfigFormStateDto,
    sqlResources: List<SqlResourceOption>,
    storageMode: String,
    sectionStateKey: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    val sqlState = buildDefaultSqlState(formState, sqlResources)
    ConfigSectionCard(
        title = "SQL по умолчанию",
        subtitle = "Используется для источников без собственного SQL.",
        sectionStateKey = sectionStateKey,
    ) {
        Div({ classes("config-form-fields") }) {
            CommitSelectField(
                label = "Источник SQL",
                value = sqlState.mode,
                options = buildDefaultSqlModeOptions(sqlState),
                disabled = disabled,
                helpText = "SQL по умолчанию для всего модуля.",
            ) { mode ->
                onCommit(applyDefaultSqlMode(formState, mode, sqlResources))
            }
            when (sqlState.mode) {
                "INLINE" -> CommitTextareaField(
                    label = "Встроенный SQL",
                    value = sqlState.inlineText,
                    disabled = disabled,
                    rowsCount = 6,
                    helpText = "Этот SQL будет применяться к источникам без собственного SQL.",
                ) { onCommit(formState.copy(commonSql = it, commonSqlFile = null)) }

                "CATALOG" -> CommitSelectField(
                    label = "SQL-ресурс",
                    value = sqlState.catalogPath,
                    options = sqlResources.map { it.path to catalogLabel(it) },
                    disabled = disabled,
                    helpText = "Выбирается из вкладки SQL.",
                ) { onCommit(formState.copy(commonSql = "", commonSqlFile = it.ifBlank { null })) }

                "EXTERNAL" -> ExternalSqlAlert(sqlState.externalRef, storageMode)
            }
        }
    }
}

@Composable
private fun SourcesSection(
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
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (disabled) {
                    disabled()
                }
                onClick {
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
            }) {
                Text("Добавить источник")
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
            Div({ classes("config-form-card", "mb-3") }) {
                Div({ classes("config-form-card-body") }) {
                    Div({ classes("d-flex", "justify-content-between", "align-items-center", "mb-3", "gap-2") }) {
                        Div {
                            Div({ classes("config-form-card-title") }) {
                                Text(source.name.ifBlank { "Источник ${index + 1}" })
                            }
                            if (!source.jdbcUrl.isBlank()) {
                                Div({ classes("config-form-help") }) {
                                    Text(source.jdbcUrl)
                                }
                            }
                        }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            if (disabled) {
                                disabled()
                            }
                            onClick {
                                onCommit(formState.copy(sources = formState.sources.filterIndexed { sourceIndex, _ -> sourceIndex != index }))
                            }
                        }) {
                            Text("Удалить")
                        }
                    }
                    Div({ classes("config-form-fields") }) {
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
                        when (sourceSqlState.mode) {
                            "INLINE" -> CommitTextareaField(
                                label = "Встроенный SQL",
                                value = sourceSqlState.inlineText,
                                disabled = disabled,
                                rowsCount = 5,
                                helpText = "Если задан, перекрывает SQL по умолчанию.",
                            ) { onCommit(updateSource(formState, index) { copy(sql = it, sqlFile = null) }) }

                            "CATALOG" -> CommitSelectField(
                                label = "SQL-ресурс",
                                value = sourceSqlState.catalogPath,
                                options = sqlResources.map { it.path to catalogLabel(it) },
                                disabled = disabled,
                                helpText = "Выбирается из вкладки SQL.",
                            ) { onCommit(updateSource(formState, index) { copy(sql = null, sqlFile = it.ifBlank { null }) }) }

                            "EXTERNAL" -> ExternalSqlAlert(sourceSqlState.externalRef, storageMode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotasSection(
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
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (disabled) {
                    disabled()
                }
                onClick {
                    onCommit(
                        formState.copy(
                            quotas = formState.quotas + ConfigFormQuotaStateDto(source = "", percent = null),
                        ),
                    )
                }
            }) {
                Text("Добавить квоту")
            }
        },
    ) {
        if (formState.quotas.isEmpty()) {
            P({ classes("text-secondary", "mb-0") }) { Text("Квоты не заданы.") }
        }
        formState.quotas.forEachIndexed { index, quota ->
            Div({ classes("config-form-card", "mb-3") }) {
                Div({ classes("config-form-card-body") }) {
                    Div({ classes("d-flex", "justify-content-between", "align-items-center", "mb-3", "gap-2") }) {
                        Div({ classes("config-form-card-title") }) {
                            Text(quota.source.ifBlank { "Квота ${index + 1}" })
                        }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            if (disabled) {
                                disabled()
                            }
                            onClick {
                                onCommit(formState.copy(quotas = formState.quotas.filterIndexed { quotaIndex, _ -> quotaIndex != index }))
                            }
                        }) { Text("Удалить") }
                    }
                    Div({ classes("config-form-fields") }) {
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
    }
}

@Composable
private fun TargetSection(
    formState: ConfigFormStateDto,
    sectionStateKey: String,
    disabled: Boolean,
    onCommit: (ConfigFormStateDto) -> Unit,
) {
    ConfigSectionCard(
        title = "Целевая загрузка",
        subtitle = "Параметры final import в целевую таблицу.",
        sectionStateKey = sectionStateKey,
    ) {
        CommitCheckboxField(
            label = "Загрузка в target включена",
            checked = formState.targetEnabled,
            disabled = disabled,
            helpText = "Если выключено, запуск остановится на merged.csv.",
        ) { onCommit(formState.copy(targetEnabled = it)) }
        Div({ classes("config-form-fields") }) {
            CommitTextField(
                label = "JDBC URL",
                value = formState.targetJdbcUrl,
                disabled = disabled,
                helpText = "Поддерживаются placeholders.",
            ) { onCommit(formState.copy(targetJdbcUrl = it)) }
            CommitTextField(
                label = "Пользователь",
                value = formState.targetUsername,
                disabled = disabled,
                helpText = "Поддерживаются placeholders.",
            ) { onCommit(formState.copy(targetUsername = it)) }
            CommitTextField(
                label = "Пароль",
                value = formState.targetPassword,
                disabled = disabled,
                helpText = "Поддерживаются placeholders.",
            ) { onCommit(formState.copy(targetPassword = it)) }
            CommitTextField(
                label = "Таблица",
                value = formState.targetTable,
                disabled = disabled,
                helpText = "Целевая таблица, в которую загружается итоговый пул.",
            ) { onCommit(formState.copy(targetTable = it)) }
        }
        CommitCheckboxField(
            label = "Очищать таблицу перед загрузкой",
            checked = formState.targetTruncateBeforeLoad,
            disabled = disabled,
            helpText = "Перед вставкой выполнить truncate.",
        ) { onCommit(formState.copy(targetTruncateBeforeLoad = it)) }
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    subtitle: String? = null,
    sectionStateKey: String? = null,
    defaultExpanded: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember(sectionStateKey) {
        mutableStateOf(loadConfigSectionExpanded(sectionStateKey, defaultExpanded))
    }
    SectionCard(
        title = title,
        subtitle = subtitle,
        actions = {
            if (actions != null) {
                actions()
            }
            if (sectionStateKey != null) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm", "config-section-toggle")
                    attr("type", "button")
                    onClick {
                        val nextValue = !expanded
                        expanded = nextValue
                        saveConfigSectionExpanded(sectionStateKey, nextValue)
                    }
                }) {
                    Text(if (expanded) "Свернуть" else "Развернуть")
                }
            }
        },
    ) {
        if (expanded) {
            content()
        }
    }
}

private fun loadConfigSectionExpanded(
    sectionStateKey: String?,
    defaultExpanded: Boolean,
): Boolean {
    if (sectionStateKey == null) {
        return defaultExpanded
    }
    return runCatching { window.localStorage.getItem(sectionStateKey) }
        .getOrNull()
        ?.let { storedValue -> storedValue == "true" }
        ?: defaultExpanded
}

private fun saveConfigSectionExpanded(
    sectionStateKey: String,
    expanded: Boolean,
) {
    runCatching { window.localStorage.setItem(sectionStateKey, expanded.toString()) }
}

@Composable
private fun CommitTextField(
    label: String,
    value: String,
    disabled: Boolean,
    helpText: String = "",
    wide: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = {
        classes("config-form-field", *if (wide) arrayOf("config-form-field-wide") else emptyArray())
    }) {
        Span({ classes("config-form-label") }) { Text(label) }
        if (helpText.isNotBlank()) {
            Span({ classes("config-form-help") }) { Text(helpText) }
        }
        Input(type = InputType.Text, attrs = {
            classes("form-control")
            value(draft)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange { if (draft != value) onCommit(draft) }
        })
    }
}

@Composable
private fun CommitTextareaField(
    label: String,
    value: String,
    disabled: Boolean,
    rowsCount: Int,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = { classes("config-form-field", "config-form-field-wide") }) {
        Span({ classes("config-form-label") }) { Text(label) }
        if (helpText.isNotBlank()) {
            Span({ classes("config-form-help") }) { Text(helpText) }
        }
        TextArea(value = draft, attrs = {
            classes("form-control")
            rows(rowsCount)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange { if (draft != value) onCommit(draft) }
        })
    }
}

@Composable
private fun CommitSelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    Label(attrs = { classes("config-form-field") }) {
        Span({ classes("config-form-label") }) { Text(label) }
        if (helpText.isNotBlank()) {
            Span({ classes("config-form-help") }) { Text(helpText) }
        }
        Select(attrs = {
            classes("form-select")
            attr("value", value)
            if (disabled) {
                disabled()
            }
            onChange { event -> onCommit(event.value ?: "") }
        }) {
            options.forEach { (optionValue, optionLabel) ->
                Option(value = optionValue) {
                    Text(optionLabel)
                }
            }
        }
    }
}

@Composable
private fun CommitCheckboxField(
    label: String,
    checked: Boolean,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Boolean) -> Unit,
) {
    Div({ classes("config-form-check-group") }) {
        Label(attrs = { classes("config-form-check") }) {
            Input(type = InputType.Checkbox, attrs = {
                classes("form-check-input")
                if (checked) {
                    attr("checked", "checked")
                }
                if (disabled) {
                    disabled()
                }
                onClick { onCommit(!checked) }
            })
            Span({ classes("form-check-label") }) { Text(label) }
        }
        if (helpText.isNotBlank()) {
            Div({ classes("config-form-help") }) { Text(helpText) }
        }
    }
}

@Composable
private fun CommitIntField(
    label: String,
    value: Int,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Int) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = value.toString(),
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        nextValue.toIntOrNull()?.let(onCommit)
    },
)

@Composable
private fun CommitOptionalIntField(
    label: String,
    value: Int?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Int?) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        if (nextValue.isBlank()) {
            onCommit(null)
        } else {
            nextValue.toIntOrNull()?.let(onCommit)
        }
    },
)

@Composable
private fun CommitLongField(
    label: String,
    value: Long,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Long) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = value.toString(),
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        nextValue.toLongOrNull()?.let(onCommit)
    },
)

@Composable
private fun CommitOptionalLongField(
    label: String,
    value: Long?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Long?) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        if (nextValue.isBlank()) {
            onCommit(null)
        } else {
            nextValue.toLongOrNull()?.let(onCommit)
        }
    },
)

@Composable
private fun CommitOptionalDoubleField(
    label: String,
    value: Double?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Double?) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        if (nextValue.isBlank()) {
            onCommit(null)
        } else {
            nextValue.toDoubleOrNull()?.let(onCommit)
        }
    },
)

@Composable
private fun CommitNumericTextField(
    label: String,
    valueText: String,
    disabled: Boolean,
    helpText: String,
    onCommitText: (String) -> Unit,
) {
    var draft by remember(valueText) { mutableStateOf(valueText) }
    Label(attrs = { classes("config-form-field") }) {
        Span({ classes("config-form-label") }) { Text(label) }
        if (helpText.isNotBlank()) {
            Span({ classes("config-form-help") }) { Text(helpText) }
        }
        Input(type = InputType.Text, attrs = {
            classes("form-control")
            value(draft)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange {
                onCommitText(draft)
                draft = draft.ifBlank { valueText }
            }
        })
    }
}

@Composable
private fun ExternalSqlAlert(
    externalRef: String?,
    storageMode: String,
) {
    if (externalRef.isNullOrBlank()) {
        return
    }
    Div({ classes("alert", if (storageMode == "database") "alert-warning" else "alert-secondary", "mb-0", "w-100") }) {
        Text(
            if (storageMode == "database") {
                "Обнаружена внешняя SQL-ссылка: $externalRef. Для DB-режима поддерживаются только встроенный SQL и SQL-ресурсы самого модуля."
            } else {
                "Используется внешняя SQL-ссылка: $externalRef. Она сохраняется, но полноценно управляется только через application.yml."
            },
        )
    }
}

private fun updateSource(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormSourceStateDto.() -> ConfigFormSourceStateDto,
): ConfigFormStateDto =
    formState.copy(
        sources = formState.sources.mapIndexed { sourceIndex, source ->
            if (sourceIndex == index) {
                source.transform()
            } else {
                source
            }
        },
    )

private fun updateQuota(
    formState: ConfigFormStateDto,
    index: Int,
    transform: ConfigFormQuotaStateDto.() -> ConfigFormQuotaStateDto,
): ConfigFormStateDto =
    formState.copy(
        quotas = formState.quotas.mapIndexed { quotaIndex, quota ->
            if (quotaIndex == index) {
                quota.transform()
            } else {
                quota
            }
        },
    )

private data class SqlResourceOption(
    val label: String,
    val path: String,
    val exists: Boolean,
)

private data class DefaultSqlState(
    val mode: String,
    val inlineText: String,
    val catalogPath: String,
    val externalRef: String? = null,
)

private data class SourceSqlState(
    val mode: String,
    val inlineText: String,
    val catalogPath: String,
    val externalRef: String? = null,
    val summary: String,
)

private fun catalogLabel(resource: SqlResourceOption): String =
    if (resource.exists) {
        resource.path
    } else {
        "[Отсутствует] ${resource.path}"
    }

private fun buildDefaultSqlState(
    formState: ConfigFormStateDto,
    sqlResources: List<SqlResourceOption>,
): DefaultSqlState {
    val inlineSql = formState.commonSql
    val sqlFile = formState.commonSqlFile?.trim().orEmpty()
    if (inlineSql.isNotBlank()) {
        return DefaultSqlState(mode = "INLINE", inlineText = inlineSql, catalogPath = "")
    }
    if (sqlFile.isBlank()) {
        return DefaultSqlState(mode = "NONE", inlineText = "", catalogPath = "")
    }
    return if (sqlResources.any { it.path == sqlFile }) {
        DefaultSqlState(mode = "CATALOG", inlineText = "", catalogPath = sqlFile)
    } else {
        DefaultSqlState(mode = "EXTERNAL", inlineText = "", catalogPath = "", externalRef = sqlFile)
    }
}

private fun buildSourceSqlState(
    source: ConfigFormSourceStateDto,
    sqlResources: List<SqlResourceOption>,
): SourceSqlState {
    val inlineSql = source.sql.orEmpty()
    val sqlFile = source.sqlFile?.trim().orEmpty()
    if (inlineSql.isNotBlank()) {
        return SourceSqlState(
            mode = "INLINE",
            inlineText = inlineSql,
            catalogPath = "",
            summary = "Использует встроенный SQL.",
        )
    }
    if (sqlFile.isBlank()) {
        return SourceSqlState(
            mode = "INHERIT",
            inlineText = "",
            catalogPath = "",
            summary = "Наследует SQL по умолчанию.",
        )
    }
    return if (sqlResources.any { it.path == sqlFile }) {
        SourceSqlState(
            mode = "CATALOG",
            inlineText = "",
            catalogPath = sqlFile,
            summary = "Использует SQL-ресурс из каталога.",
        )
    } else {
        SourceSqlState(
            mode = "EXTERNAL",
            inlineText = "",
            catalogPath = "",
            externalRef = sqlFile,
            summary = "Использует внешнюю SQL-ссылку.",
        )
    }
}

private fun buildDefaultSqlModeOptions(sqlState: DefaultSqlState): List<Pair<String, String>> =
    buildList {
        add("NONE" to "Не задан")
        add("INLINE" to "Встроенный SQL")
        add("CATALOG" to "SQL из каталога")
        if (sqlState.mode == "EXTERNAL") {
            add("EXTERNAL" to "Внешняя ссылка")
        }
    }

private fun buildSourceSqlModeOptions(sqlState: SourceSqlState): List<Pair<String, String>> =
    buildList {
        add("INHERIT" to "Наследовать SQL по умолчанию")
        add("INLINE" to "Встроенный SQL")
        add("CATALOG" to "SQL из каталога")
        if (sqlState.mode == "EXTERNAL") {
            add("EXTERNAL" to "Внешняя ссылка")
        }
    }

private fun applyDefaultSqlMode(
    formState: ConfigFormStateDto,
    mode: String,
    sqlResources: List<SqlResourceOption>,
): ConfigFormStateDto =
    when (mode) {
        "INLINE" -> formState.copy(commonSql = formState.commonSql, commonSqlFile = null)
        "CATALOG" -> formState.copy(
            commonSql = "",
            commonSqlFile = formState.commonSqlFile
                ?.takeIf { path -> sqlResources.any { it.path == path } }
                ?: sqlResources.firstOrNull()?.path,
        )
        "NONE" -> formState.copy(commonSql = "", commonSqlFile = null)
        else -> formState
    }

private fun applySourceSqlMode(
    formState: ConfigFormStateDto,
    index: Int,
    mode: String,
    sqlResources: List<SqlResourceOption>,
): ConfigFormStateDto =
    updateSource(formState, index) {
        when (mode) {
            "INLINE" -> copy(sql = sql.orEmpty(), sqlFile = null)
            "CATALOG" -> copy(
                sql = null,
                sqlFile = sqlFile
                    ?.takeIf { path -> sqlResources.any { it.path == path } }
                    ?: sqlResources.firstOrNull()?.path,
            )
            "INHERIT" -> copy(sql = null, sqlFile = null)
            else -> this
        }
    }

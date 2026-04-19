package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun GeneralSettingsSection(
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
internal fun DefaultSqlSection(
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
            CommitSqlModeFields(
                mode = sqlState.mode,
                inlineText = sqlState.inlineText,
                catalogPath = sqlState.catalogPath,
                externalRef = sqlState.externalRef,
                sqlResources = sqlResources,
                disabled = disabled,
                storageMode = storageMode,
                inlineRowsCount = 6,
                inlineHelpText = "Этот SQL будет применяться к источникам без собственного SQL.",
                onInlineCommit = { onCommit(formState.copy(commonSql = it, commonSqlFile = null)) },
                onCatalogCommit = { onCommit(formState.copy(commonSql = "", commonSqlFile = it.ifBlank { null })) },
            )
        }
    }
}

@Composable
internal fun TargetSection(
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

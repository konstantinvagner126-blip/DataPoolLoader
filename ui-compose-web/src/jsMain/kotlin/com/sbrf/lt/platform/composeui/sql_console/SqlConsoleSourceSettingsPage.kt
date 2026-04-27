package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeSqlConsoleSourceSettingsPage(
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val store = remember(api) { SqlConsoleSourceSettingsStore(api) }
    val workspaceId = remember { resolveSqlConsoleWorkspaceId() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SqlConsoleSourceSettingsPageState()) }

    fun runAction(
        start: (SqlConsoleSourceSettingsPageState) -> SqlConsoleSourceSettingsPageState,
        action: suspend (SqlConsoleSourceSettingsPageState) -> SqlConsoleSourceSettingsPageState,
    ) {
        scope.launch {
            state = start(state)
            state = runCatching { action(state) }.getOrElse { error -> store.fail(state, error) }
        }
    }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = runCatching { store.load(state) }.getOrElse { error -> store.fail(state, error) }
    }

    PageScaffold(
        eyebrow = "MLP Platform",
        title = "Источники SQL-консоли",
        subtitle = "Редактор source catalog и credentials path для SQL-консоли.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                SqlConsoleNavActionButton("На главную", hrefValue = "/")
                SqlConsoleNavActionButton("SQL-консоль", hrefValue = buildSqlConsoleWorkspaceHref(workspaceId))
                SqlConsoleNavActionButton("Объекты БД", hrefValue = buildSqlConsoleObjectsWorkspaceHref(workspaceId))
                SqlConsoleNavActionButton("Источники", active = true)
            }
        },
        content = {
            SqlConsoleSourceSettingsContent(
                state = state,
                onReload = {
                    runAction(
                        start = store::startLoading,
                        action = { store.load(it) },
                    )
                },
                onSave = {
                    runAction(
                        start = store::startSaving,
                        action = { store.save(it) },
                    )
                },
                onTestAllSources = {
                    runAction(
                        start = store::startConnectionsTest,
                        action = { store.testConnections(it) },
                    )
                },
                onAddSource = { state = store.addSource(state) },
                onRemoveSource = { index -> state = store.removeSource(state, index) },
                onSourceChange = { index, transform -> state = store.updateSource(state, index, transform) },
                onAddGroup = { state = store.addGroup(state) },
                onRemoveGroup = { index -> state = store.removeGroup(state, index) },
                onGroupChange = { index, transform -> state = store.updateGroup(state, index, transform) },
                onDefaultCredentialsFileChange = { value ->
                    state = store.updateDefaultCredentialsFile(state, value)
                },
                onPickCredentialsFile = {
                    runAction(
                        start = store::startFilePick,
                        action = { store.pickCredentialsFile(it) },
                    )
                },
                onDiagnoseCredentials = {
                    runAction(
                        start = store::startCredentialsDiagnostics,
                        action = { store.diagnoseCredentials(it) },
                    )
                },
                onTestSource = { index ->
                    runAction(
                        start = { store.startConnectionTest(it, index) },
                        action = { store.testConnection(it, index) },
                    )
                },
            )
        },
    )
}

@Composable
private fun SqlConsoleSourceSettingsContent(
    state: SqlConsoleSourceSettingsPageState,
    onReload: () -> Unit,
    onSave: () -> Unit,
    onTestAllSources: () -> Unit,
    onAddSource: () -> Unit,
    onRemoveSource: (Int) -> Unit,
    onSourceChange: (Int, (SqlConsoleEditableSource) -> SqlConsoleEditableSource) -> Unit,
    onAddGroup: () -> Unit,
    onRemoveGroup: (Int) -> Unit,
    onGroupChange: (Int, (SqlConsoleEditableSourceGroup) -> SqlConsoleEditableSourceGroup) -> Unit,
    onDefaultCredentialsFileChange: (String) -> Unit,
    onPickCredentialsFile: () -> Unit,
    onDiagnoseCredentials: () -> Unit,
    onTestSource: (Int) -> Unit,
) {
    state.errorMessage?.let { AlertBanner(it, "warning") }
    state.successMessage?.let { AlertBanner(it, "success") }

    Div({ classes("sql-source-settings-toolbar") }) {
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.loading) disabled()
            onClick { onReload() }
        }) { Text("Перечитать") }
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.loading) disabled()
            onClick { onAddSource() }
        }) { Text("Добавить source") }
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.loading) disabled()
            onClick { onAddGroup() }
        }) { Text("Добавить группу") }
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            if (state.loading) disabled()
            onClick { onTestAllSources() }
        }) { Text("Проверить все sources") }
        Button(attrs = {
            classes("btn", "btn-dark", "btn-sm")
            attr("type", "button")
            if (state.loading) disabled()
            onClick { onSave() }
        }) { Text(if (state.loading) "Сохраняю..." else "Сохранить") }
    }

    val settings = state.settings
    if (state.loading && settings == null) {
        P({ classes("text-secondary", "small", "mb-0") }) { Text("Загружаю настройки sources.") }
        return
    }
    if (settings == null) {
        EmptyStateCard(
            title = "Источники SQL-консоли",
            text = "Настройки пока не загружены.",
        )
        return
    }

    settings.editableConfigPath?.let { path ->
        P({ classes("sql-source-settings-path") }) { Text("Редактируемый конфиг: $path") }
    }

    SqlConsoleCredentialsPathCard(
        value = settings.defaultCredentialsFile,
        busy = state.loading || state.filePickInProgress,
        diagnostics = state.credentialsDiagnostics,
        onValueChange = onDefaultCredentialsFileChange,
        onPickFile = onPickCredentialsFile,
        onDiagnose = onDiagnoseCredentials,
    )

    state.connectionsTestResult?.let { result ->
        SqlConsoleConnectionsTestSummary(result)
    }

    Div({ classes("sql-source-settings-layout") }) {
        Div({ classes("sql-source-settings-panel") }) {
            Div({ classes("sql-source-settings-section-head") }) {
                Span { Text("Sources") }
                Span({ classes("sql-source-settings-count") }) { Text(settings.sources.size.toString()) }
            }
            if (settings.sources.isEmpty()) {
                EmptyStateCard("Sources", "Добавь хотя бы один source.")
            } else {
                settings.sources.forEachIndexed { index, source ->
                    SqlConsoleSourceSettingsCard(
                        index = index,
                        source = source,
                        secretProvider = settings.secretProvider,
                        referencingGroups = settings.groups.groupsReferencingSource(source),
                        busy = state.loading,
                        connectionResult = if (state.connectionTestSourceIndex == index) {
                            state.connectionTestResult
                        } else {
                            null
                        },
                        allSourcesConnectionStatus = state.connectionsTestResult
                            ?.sourceResults
                            ?.firstOrNull { it.sourceName.trim() == source.name.trim() },
                        onChange = { transform -> onSourceChange(index, transform) },
                        onRemove = { onRemoveSource(index) },
                        onTest = { onTestSource(index) },
                    )
                }
            }
        }

        Div({ classes("sql-source-settings-panel") }) {
            Div({ classes("sql-source-settings-section-head") }) {
                Span { Text("Groups") }
                Span({ classes("sql-source-settings-count") }) { Text(settings.groups.size.toString()) }
            }
            val sourceNames = settings.sources.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
            if (settings.groups.isEmpty()) {
                EmptyStateCard("Groups", "Группы не настроены.")
            } else {
                settings.groups.forEachIndexed { index, group ->
                    SqlConsoleSourceGroupSettingsCard(
                        group = group,
                        sourceNames = sourceNames,
                        busy = state.loading,
                        onChange = { transform -> onGroupChange(index, transform) },
                        onRemove = { onRemoveGroup(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleCredentialsPathCard(
    value: String,
    busy: Boolean,
    diagnostics: SqlConsoleSourceSettingsCredentialsDiagnosticsResponse?,
    onValueChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onDiagnose: () -> Unit,
) {
    Div({ classes("sql-source-settings-card", "sql-source-settings-credentials") }) {
        Div({ classes("sql-source-settings-card-head") }) {
            Div {
                Div({ classes("sql-source-settings-caption") }) { Text("credential.properties") }
                Div({ classes("sql-source-settings-title") }) { Text("Файл credentials") }
            }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (busy) disabled()
                onClick { onPickFile() }
            }) { Text("Выбрать файл") }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (busy) disabled()
                onClick { onDiagnose() }
            }) { Text("Проверить") }
        }
        SqlConsoleSourceSettingsTextField(
            label = "ui.defaultCredentialsFile",
            value = value,
            placeholderText = "/path/to/credential.properties",
            disabled = busy,
            onValueChange = onValueChange,
        )
        diagnostics?.let { SqlConsoleCredentialsDiagnosticsSummary(it) }
    }
}

@Composable
private fun SqlConsoleCredentialsDiagnosticsSummary(
    diagnostics: SqlConsoleSourceSettingsCredentialsDiagnosticsResponse,
) {
    Div({
        classes(
            "sql-source-settings-diagnostics",
            if (diagnostics.fileAvailable && diagnostics.missingKeys.isEmpty()) {
                "sql-source-settings-diagnostics-ok"
            } else {
                "sql-source-settings-diagnostics-warn"
            },
        )
    }) {
        Div({ classes("sql-source-settings-diagnostics-message") }) {
            Text(diagnostics.message)
        }
        diagnostics.resolvedPath?.let { path ->
            Div({ classes("sql-source-settings-diagnostics-row") }) {
                Span { Text("Resolved path") }
                Span { Text(path) }
            }
        }
        SqlConsoleDiagnosticsKeyRow("Required keys", diagnostics.requiredKeys)
        SqlConsoleDiagnosticsKeyRow("Missing keys", diagnostics.missingKeys)
    }
}

@Composable
private fun SqlConsoleDiagnosticsKeyRow(
    label: String,
    keys: List<String>,
) {
    Div({ classes("sql-source-settings-diagnostics-row") }) {
        Span { Text(label) }
        Span { Text(keys.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "-") }
    }
}

@Composable
private fun SqlConsoleConnectionsTestSummary(
    result: SqlConsoleSourceSettingsConnectionsTestResponse,
) {
    Div({
        classes(
            "sql-source-settings-card",
            "sql-source-settings-test-summary",
            if (result.success) "sql-source-settings-test-summary-ok" else "sql-source-settings-test-summary-warn",
        )
    }) {
        Div({ classes("sql-source-settings-diagnostics-message") }) { Text(result.message) }
        result.sourceResults.forEach { source ->
            Div({ classes("sql-source-settings-test-row") }) {
                Span({ classes("sql-source-settings-test-source") }) { Text(source.sourceName) }
                Span({
                    classes(
                        "sql-source-settings-test-status",
                        if (isSuccessfulSourceSettingsStatus(source.status)) "ok" else "failed",
                    )
                }) { Text(source.status) }
                Span({ classes("sql-source-settings-test-message") }) {
                    Text(source.message ?: source.errorMessage ?: "")
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleSourceSettingsCard(
    index: Int,
    source: SqlConsoleEditableSource,
    secretProvider: SqlConsoleSecretProvider,
    referencingGroups: List<String>,
    busy: Boolean,
    connectionResult: SqlConsoleSourceSettingsConnectionTestResponse?,
    allSourcesConnectionStatus: SqlConsoleSourceConnectionStatus?,
    onChange: ((SqlConsoleEditableSource) -> SqlConsoleEditableSource) -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
) {
    val credentialsMode = source.credentialsMode.normalizedSqlSourceCredentialsMode()
    var passwordVisible by remember(source.originalName, source.name) { mutableStateOf(false) }
    var technicalDetailsVisible by remember(source.originalName, source.name) { mutableStateOf(false) }

    Div({ classes("sql-source-settings-card") }) {
        Div({ classes("sql-source-settings-card-head") }) {
            Div {
                Div({ classes("sql-source-settings-caption") }) { Text("Source ${index + 1}") }
                Div({ classes("sql-source-settings-title") }) {
                    Text(source.name.ifBlank { "Новый source" })
                }
            }
            Div({ classes("sql-source-settings-actions") }) {
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    if (busy) disabled()
                    onClick { onTest() }
                }) { Text("Тест подключения") }
                Button(attrs = {
                    classes("btn", "btn-outline-danger", "btn-sm")
                    attr("type", "button")
                    if (busy) disabled()
                    onClick { onRemove() }
                }) { Text("Удалить") }
            }
        }

        connectionResult?.let { result ->
            AlertBanner(
                text = result.message,
                level = if (result.success) "success" else "warning",
            )
        }
        allSourcesConnectionStatus?.let { status ->
            SqlConsoleSourceConnectionStatusInline(status)
        }

        Div({ classes("sql-source-settings-grid") }) {
            SqlConsoleSourceSettingsTextField("name", source.name, "db1", busy) {
                onChange { source -> source.copy(name = it) }
            }
            SqlConsoleSourceSettingsSelectField(
                label = "credentials",
                value = credentialsMode,
                options = listOf(
                    SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN to "System keychain",
                    SQL_SOURCE_CREDENTIALS_MODE_PLACEHOLDERS to "credential.properties",
                ),
                disabled = busy,
            ) { value ->
                val nextMode = value.normalizedSqlSourceCredentialsMode()
                onChange { source ->
                    source.copy(
                        credentialsMode = nextMode,
                        passwordReference = if (nextMode == SQL_SOURCE_CREDENTIALS_MODE_PLACEHOLDERS) {
                            source.passwordReference
                        } else {
                            ""
                        },
                        passwordPlainText = "",
                    )
                }
            }
            if (credentialsMode == SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN) {
                SqlConsoleSourceSettingsTextField("jdbcUrl", source.jdbcUrl, "jdbc:postgresql://host:5432/db", busy) {
                    onChange { source -> source.copy(jdbcUrl = it) }
                }
                SqlConsoleSourceSettingsTextField("username", source.username, "user", busy) {
                    onChange { source -> source.copy(username = it) }
                }
                SqlConsoleSourceSettingsTextField(
                    label = "secret key",
                    value = source.secretKey,
                    placeholderText = "sqlConsole.sources.${source.name.ifBlank { "source" }}.password",
                    disabled = busy,
                ) {
                    onChange { source -> source.copy(secretKey = it) }
                }
                SqlConsoleSourcePasswordField(
                    label = "password",
                    value = source.passwordPlainText,
                    placeholderText = if (source.secretConfigured) {
                        "сохранен, оставь пустым чтобы не менять"
                    } else {
                        "введи пароль"
                    },
                    visible = passwordVisible,
                    disabled = busy,
                    onVisibilityChange = { passwordVisible = it },
                ) {
                    onChange { source -> source.copy(passwordPlainText = it) }
                }
            } else {
                SqlConsoleSourceSettingsTextField("jdbcUrl placeholder", source.jdbcUrl, "\${db.jdbcUrl}", busy) {
                    onChange { source -> source.copy(jdbcUrl = it) }
                }
                SqlConsoleSourceSettingsTextField("username placeholder", source.username, "\${db.user}", busy) {
                    onChange { source -> source.copy(username = it) }
                }
                SqlConsoleSourceSettingsTextField(
                    label = "password placeholder",
                    value = source.passwordReference,
                    placeholderText = if (source.passwordConfigured) "сохранено, оставь пустым чтобы не менять" else "\${db.password}",
                    disabled = busy,
                ) {
                    onChange { source -> source.copy(passwordReference = it) }
                }
            }
        }
        P({ classes("sql-source-settings-meta") }) {
            if (credentialsMode == SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN) {
                val passwordStatus = if (source.secretConfigured) "password saved" else "password missing"
                Text(
                    "Password storage: System keychain · Provider: ${secretProvider.displayName} · $passwordStatus",
                )
            } else {
                Text("Password storage: credential.properties")
            }
        }
        if (credentialsMode == SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN && !secretProvider.available) {
            P({ classes("sql-source-settings-warning") }) {
                Text(secretProvider.unavailableReason ?: "${secretProvider.displayName} недоступен.")
            }
        }
        if (credentialsMode == SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN) {
            SqlConsoleSourceTechnicalDetails(
                source = source,
                visible = technicalDetailsVisible,
                onVisibilityChange = { technicalDetailsVisible = it },
            )
        }
        if (referencingGroups.isNotEmpty()) {
            P({ classes("sql-source-settings-warning") }) {
                Text(
                    "Используется в группах: ${referencingGroups.joinToString(", ")}. " +
                        "При удалении source будет удален из этих групп.",
                )
            }
        }
    }
}

@Composable
private fun SqlConsoleSourceTechnicalDetails(
    source: SqlConsoleEditableSource,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
) {
    Div({ classes("sql-source-settings-technical") }) {
        Button(attrs = {
            classes("btn", "btn-outline-secondary", "btn-sm")
            attr("type", "button")
            onClick { onVisibilityChange(!visible) }
        }) {
            Text(if (visible) "Скрыть technical details" else "Показать technical details")
        }
        if (visible) {
            Div({ classes("sql-source-settings-technical-body") }) {
                SqlConsoleSourceTechnicalRow("Secret key", source.secretKey.ifBlank { "-" })
                SqlConsoleSourceTechnicalRow(
                    label = "Config value",
                    value = source.secretKey
                        .takeIf { it.isNotBlank() }
                        ?.let { "\${secret:$it}" }
                        ?: "-",
                )
            }
        }
    }
}

@Composable
private fun SqlConsoleSourceTechnicalRow(
    label: String,
    value: String,
) {
    Div({ classes("sql-source-settings-technical-row") }) {
        Span { Text(label) }
        Pre({ classes("mb-0") }) { Text(value) }
    }
}

@Composable
private fun SqlConsoleSourceConnectionStatusInline(
    status: SqlConsoleSourceConnectionStatus,
) {
    Div({ classes("sql-source-settings-inline-status") }) {
        Span({
            classes(
                "sql-source-settings-test-status",
                if (isSuccessfulSourceSettingsStatus(status.status)) "ok" else "failed",
            )
        }) {
            Text(status.status)
        }
        Span({ classes("sql-source-settings-test-message") }) {
            Text(status.message ?: status.errorMessage ?: "")
        }
    }
}

private fun isSuccessfulSourceSettingsStatus(status: String): Boolean =
    status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true)

@Composable
private fun SqlConsoleSourceGroupSettingsCard(
    group: SqlConsoleEditableSourceGroup,
    sourceNames: Set<String>,
    busy: Boolean,
    onChange: ((SqlConsoleEditableSourceGroup) -> SqlConsoleEditableSourceGroup) -> Unit,
    onRemove: () -> Unit,
) {
    val unknownSources = group.sources.map { it.trim() }.filter { it.isNotEmpty() && it !in sourceNames }
    Div({ classes("sql-source-settings-card") }) {
        Div({ classes("sql-source-settings-card-head") }) {
            Div {
                Div({ classes("sql-source-settings-caption") }) { Text("Group") }
                Div({ classes("sql-source-settings-title") }) { Text(group.name.ifBlank { "Новая группа" }) }
            }
            Button(attrs = {
                classes("btn", "btn-outline-danger", "btn-sm")
                attr("type", "button")
                if (busy) disabled()
                onClick { onRemove() }
            }) { Text("Удалить") }
        }
        Div({ classes("sql-source-settings-grid") }) {
            SqlConsoleSourceSettingsTextField("name", group.name, "main", busy) {
                onChange { group -> group.copy(name = it) }
            }
            SqlConsoleSourceSettingsTextField(
                label = "sources",
                value = group.sources.joinToString(", "),
                placeholderText = "db1, db2",
                disabled = busy,
            ) { raw ->
                onChange { group -> group.copy(sources = parseSqlConsoleGroupSources(raw)) }
            }
        }
        if (group.sources.isEmpty()) {
            P({ classes("sql-source-settings-warning") }) { Text("Группа пустая.") }
        } else if (unknownSources.isNotEmpty()) {
            P({ classes("sql-source-settings-warning") }) {
                Text("Неизвестные source: ${unknownSources.joinToString(", ")}")
            }
        }
    }
}

@Composable
private fun SqlConsoleSourceSettingsTextField(
    label: String,
    value: String,
    placeholderText: String,
    disabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Div({ classes("sql-source-settings-field") }) {
        Span({ classes("sql-source-settings-label") }) { Text(label) }
        Input(type = InputType.Text, attrs = {
            classes("form-control", "form-control-sm")
            value(value)
            placeholder(placeholderText)
            if (disabled) disabled()
            onInput { onValueChange(it.value) }
        })
    }
}

@Composable
private fun SqlConsoleSourceSettingsSelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    disabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Div({ classes("sql-source-settings-field") }) {
        Span({ classes("sql-source-settings-label") }) { Text(label) }
        Select(attrs = {
            classes("form-select", "form-select-sm")
            if (disabled) disabled()
            onChange { onValueChange(it.value.orEmpty()) }
        }) {
            options.forEach { (optionValue, optionLabel) ->
                Option(
                    value = optionValue,
                    attrs = { if (optionValue == value) selected() },
                ) {
                    Text(optionLabel)
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleSourcePasswordField(
    label: String,
    value: String,
    placeholderText: String,
    visible: Boolean,
    disabled: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    Div({ classes("sql-source-settings-field") }) {
        Span({ classes("sql-source-settings-label") }) { Text(label) }
        Div({ classes("sql-source-settings-password-row") }) {
            Input(type = if (visible) InputType.Text else InputType.Password, attrs = {
                classes("form-control", "form-control-sm")
                value(value)
                placeholder(placeholderText)
                if (disabled) disabled()
                onInput { onValueChange(it.value) }
            })
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                if (disabled) disabled()
                onClick { onVisibilityChange(!visible) }
            }) {
                Text(if (visible) "Скрыть" else "Показать")
            }
        }
    }
}

private fun parseSqlConsoleGroupSources(rawValue: String): List<String> =
    rawValue
        .split(",", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun List<SqlConsoleEditableSourceGroup>.groupsReferencingSource(
    source: SqlConsoleEditableSource,
): List<String> {
    val sourceNames = setOf(source.originalName.trim(), source.name.trim()).filter { it.isNotEmpty() }.toSet()
    return mapNotNull { group ->
        val groupName = group.name.trim()
        val referenced = group.sources.any { it.trim() in sourceNames }
        groupName.takeIf { referenced && it.isNotEmpty() }
    }
}

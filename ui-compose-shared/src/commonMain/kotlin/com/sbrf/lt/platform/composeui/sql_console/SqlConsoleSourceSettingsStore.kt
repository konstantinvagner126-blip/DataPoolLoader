package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleSourceSettingsStore(
    private val api: SqlConsoleApi,
) {
    fun startLoading(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = true,
            errorMessage = null,
            successMessage = null,
        )

    suspend fun load(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            successMessage = null,
            settings = api.loadSourceSettings(),
            connectionTestSourceIndex = null,
            connectionTestResult = null,
            connectionsTestResult = null,
            credentialsDiagnostics = null,
            filePickInProgress = false,
        )

    fun updateDefaultCredentialsFile(
        current: SqlConsoleSourceSettingsPageState,
        value: String,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current
        return current.withEditedSettings(settings.copy(defaultCredentialsFile = value))
    }

    fun addSource(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: SqlConsoleSourceSettings()
        return current.withEditedSettings(
            settings.copy(
                sources = settings.sources + SqlConsoleEditableSource(
                    credentialsMode = SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN,
                ),
            ),
        )
    }

    fun removeSource(
        current: SqlConsoleSourceSettingsPageState,
        sourceIndex: Int,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current
        if (sourceIndex !in settings.sources.indices) {
            return current
        }
        val source = settings.sources[sourceIndex]
        val removedNames = source.removableNames()
        return current.withEditedSettings(
            settings.copy(
                sources = settings.sources.filterIndexed { index, _ -> index != sourceIndex },
                groups = settings.groups.map { group ->
                    group.copy(sources = group.sources.filter { it.trim() !in removedNames })
                },
            ),
        )
    }

    fun updateSource(
        current: SqlConsoleSourceSettingsPageState,
        sourceIndex: Int,
        transform: (SqlConsoleEditableSource) -> SqlConsoleEditableSource,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current
        if (sourceIndex !in settings.sources.indices) {
            return current
        }
        val updatedSources = settings.sources.mapIndexed { index, source ->
            if (index == sourceIndex) transform(source) else source
        }
        return current.withEditedSettings(settings.copy(sources = updatedSources))
    }

    fun addGroup(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: SqlConsoleSourceSettings()
        return current.withEditedSettings(
            settings.copy(
                groups = settings.groups + SqlConsoleEditableSourceGroup(),
            ),
        )
    }

    fun removeGroup(
        current: SqlConsoleSourceSettingsPageState,
        groupIndex: Int,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current
        if (groupIndex !in settings.groups.indices) {
            return current
        }
        return current.withEditedSettings(
            settings.copy(
                groups = settings.groups.filterIndexed { index, _ -> index != groupIndex },
            ),
        )
    }

    fun updateGroup(
        current: SqlConsoleSourceSettingsPageState,
        groupIndex: Int,
        transform: (SqlConsoleEditableSourceGroup) -> SqlConsoleEditableSourceGroup,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current
        if (groupIndex !in settings.groups.indices) {
            return current
        }
        val updatedGroups = settings.groups.mapIndexed { index, group ->
            if (index == groupIndex) transform(group) else group
        }
        return current.withEditedSettings(settings.copy(groups = updatedGroups))
    }

    fun startSaving(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = true,
            errorMessage = null,
            successMessage = null,
        )

    suspend fun save(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current.copy(loading = false)
        val saved = api.saveSourceSettings(settings.toUpdate())
        return current.copy(
            loading = false,
            errorMessage = null,
            successMessage = "Настройки источников SQL-консоли сохранены.",
            settings = saved,
            connectionTestSourceIndex = null,
            connectionTestResult = null,
            connectionsTestResult = null,
            credentialsDiagnostics = null,
            filePickInProgress = false,
        )
    }

    fun startConnectionTest(
        current: SqlConsoleSourceSettingsPageState,
        sourceIndex: Int,
    ): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = true,
            errorMessage = null,
            successMessage = null,
            connectionTestSourceIndex = sourceIndex,
            connectionTestResult = null,
        )

    suspend fun testConnection(
        current: SqlConsoleSourceSettingsPageState,
        sourceIndex: Int,
    ): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current.copy(loading = false, connectionTestSourceIndex = null)
        val source = settings.sources.getOrNull(sourceIndex)
            ?: return current.copy(loading = false, connectionTestSourceIndex = null)
        val result = api.testSourceSettingsConnection(
            SqlConsoleSourceSettingsConnectionTestRequest(
                defaultCredentialsFile = settings.defaultCredentialsFile,
                source = source.toUpdate(),
            ),
        )
        return current.copy(
            loading = false,
            connectionTestSourceIndex = sourceIndex,
            connectionTestResult = result,
            connectionsTestResult = null,
            successMessage = null,
        )
    }

    fun startConnectionsTest(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = true,
            errorMessage = null,
            successMessage = null,
            connectionTestSourceIndex = null,
            connectionTestResult = null,
            connectionsTestResult = null,
        )

    suspend fun testConnections(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current.copy(loading = false)
        val result = api.testSourceSettingsConnections(
            SqlConsoleSourceSettingsConnectionsTestRequest(settings = settings.toUpdate()),
        )
        return current.copy(
            loading = false,
            connectionsTestResult = result,
            connectionTestSourceIndex = null,
            connectionTestResult = null,
            successMessage = null,
        )
    }

    fun startCredentialsDiagnostics(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = true,
            errorMessage = null,
            successMessage = null,
            credentialsDiagnostics = null,
        )

    suspend fun diagnoseCredentials(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current.copy(loading = false)
        val diagnostics = api.diagnoseSourceSettingsCredentials(
            SqlConsoleSourceSettingsCredentialsDiagnosticsRequest(settings = settings.toUpdate()),
        )
        return current.copy(
            loading = false,
            credentialsDiagnostics = diagnostics,
            successMessage = null,
        )
    }

    fun startFilePick(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState =
        current.copy(
            errorMessage = null,
            successMessage = null,
            filePickInProgress = true,
        )

    suspend fun pickCredentialsFile(current: SqlConsoleSourceSettingsPageState): SqlConsoleSourceSettingsPageState {
        val settings = current.settings ?: return current.copy(filePickInProgress = false)
        val result = api.pickSourceSettingsCredentialsFile(
            SqlConsoleSourceSettingsFilePickRequest(currentValue = settings.defaultCredentialsFile),
        )
        if (result.cancelled || result.configValue.isNullOrBlank()) {
            return current.copy(filePickInProgress = false)
        }
        return updateDefaultCredentialsFile(current, result.configValue!!).copy(filePickInProgress = false)
    }

    fun fail(
        current: SqlConsoleSourceSettingsPageState,
        error: Throwable,
    ): SqlConsoleSourceSettingsPageState =
        current.copy(
            loading = false,
            errorMessage = error.message ?: "Ошибка настроек источников SQL-консоли.",
            filePickInProgress = false,
        )

    private fun SqlConsoleSourceSettingsPageState.withEditedSettings(
        nextSettings: SqlConsoleSourceSettings,
    ): SqlConsoleSourceSettingsPageState =
        copy(
            settings = nextSettings,
            errorMessage = null,
            successMessage = null,
            connectionTestSourceIndex = null,
            connectionTestResult = null,
            connectionsTestResult = null,
            credentialsDiagnostics = null,
        )
}

private fun SqlConsoleSourceSettings.toUpdate(): SqlConsoleSourceSettingsUpdate =
    SqlConsoleSourceSettingsUpdate(
        defaultCredentialsFile = defaultCredentialsFile,
        sources = sources.map { it.toUpdate() },
        groups = groups.map { it.toUpdate() },
    )

private fun SqlConsoleEditableSource.toUpdate(): SqlConsoleEditableSourceUpdate =
    SqlConsoleEditableSourceUpdate(
        originalName = originalName,
        name = name,
        credentialsMode = credentialsMode,
        jdbcUrl = jdbcUrl,
        username = username,
        passwordReference = passwordReference,
        keepExistingPassword = when (credentialsMode.normalizedSqlSourceCredentialsMode()) {
            SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN -> passwordPlainText.isBlank() && secretConfigured
            else -> passwordReference.isBlank() && passwordConfigured && !secretConfigured
        },
        secretKey = secretKey,
        passwordPlainText = passwordPlainText,
    )

private fun SqlConsoleEditableSourceGroup.toUpdate(): SqlConsoleEditableSourceGroupUpdate =
    SqlConsoleEditableSourceGroupUpdate(
        originalName = originalName,
        name = name,
        sources = sources,
    )

const val SQL_SOURCE_CREDENTIALS_MODE_PLACEHOLDERS = "PLACEHOLDERS"
const val SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN = "SYSTEM_KEYCHAIN"

fun String.normalizedSqlSourceCredentialsMode(): String =
    trim().uppercase().takeIf { it == SQL_SOURCE_CREDENTIALS_MODE_SYSTEM_KEYCHAIN }
        ?: SQL_SOURCE_CREDENTIALS_MODE_PLACEHOLDERS

private fun SqlConsoleEditableSource.removableNames(): Set<String> =
    setOf(originalName.trim(), name.trim()).filter { it.isNotEmpty() }.toSet()

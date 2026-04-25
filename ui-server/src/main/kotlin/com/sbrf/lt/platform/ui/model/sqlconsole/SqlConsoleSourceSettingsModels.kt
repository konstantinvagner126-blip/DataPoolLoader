package com.sbrf.lt.platform.ui.model

data class SqlConsoleSourceSettingsResponse(
    val editableConfigPath: String? = null,
    val defaultCredentialsFile: String = "",
    val secretProvider: SqlConsoleSecretProviderResponse = SqlConsoleSecretProviderResponse(),
    val sources: List<SqlConsoleEditableSourceResponse> = emptyList(),
    val groups: List<SqlConsoleEditableSourceGroupResponse> = emptyList(),
)

data class SqlConsoleSecretProviderResponse(
    val providerId: String = "unsupported",
    val displayName: String = "System secret storage",
    val available: Boolean = false,
    val unavailableReason: String? = null,
)

data class SqlConsoleEditableSourceResponse(
    val originalName: String = "",
    val name: String = "",
    val credentialsMode: String = "PLACEHOLDERS",
    val jdbcUrl: String = "",
    val username: String = "",
    val passwordReference: String = "",
    val passwordConfigured: Boolean = false,
    val secretKey: String = "",
    val secretConfigured: Boolean = false,
    val passwordPlainText: String = "",
)

data class SqlConsoleEditableSourceGroupResponse(
    val originalName: String = "",
    val name: String = "",
    val sources: List<String> = emptyList(),
)

data class SqlConsoleSourceSettingsUpdateRequest(
    val defaultCredentialsFile: String = "",
    val sources: List<SqlConsoleEditableSourceRequest> = emptyList(),
    val groups: List<SqlConsoleEditableSourceGroupRequest> = emptyList(),
)

data class SqlConsoleEditableSourceRequest(
    val originalName: String = "",
    val name: String = "",
    val credentialsMode: String = "PLACEHOLDERS",
    val jdbcUrl: String = "",
    val username: String = "",
    val passwordReference: String = "",
    val keepExistingPassword: Boolean = true,
    val secretKey: String = "",
    val passwordPlainText: String = "",
)

data class SqlConsoleEditableSourceGroupRequest(
    val originalName: String = "",
    val name: String = "",
    val sources: List<String> = emptyList(),
)

data class SqlConsoleSourceSettingsConnectionTestRequest(
    val defaultCredentialsFile: String = "",
    val source: SqlConsoleEditableSourceRequest,
)

data class SqlConsoleSourceSettingsConnectionTestResponse(
    val success: Boolean,
    val sourceName: String,
    val message: String,
)

data class SqlConsoleSourceSettingsConnectionsTestRequest(
    val settings: SqlConsoleSourceSettingsUpdateRequest,
)

data class SqlConsoleSourceSettingsConnectionsTestResponse(
    val success: Boolean,
    val message: String,
    val sourceResults: List<SqlConsoleSourceConnectionStatusResponse> = emptyList(),
)

data class SqlConsoleSourceSettingsCredentialsDiagnosticsRequest(
    val settings: SqlConsoleSourceSettingsUpdateRequest,
)

data class SqlConsoleSourceSettingsCredentialsDiagnosticsResponse(
    val configuredPath: String = "",
    val resolvedPath: String? = null,
    val fileAvailable: Boolean = false,
    val requiredKeys: List<String> = emptyList(),
    val availableKeys: List<String> = emptyList(),
    val missingKeys: List<String> = emptyList(),
    val message: String,
)

data class SqlConsoleSourceSettingsFilePickRequest(
    val currentValue: String = "",
)

data class SqlConsoleSourceSettingsFilePickResponse(
    val cancelled: Boolean,
    val selectedPath: String? = null,
    val configValue: String? = null,
)

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiSecretProvider
import com.sbrf.lt.platform.ui.config.UiSecretProviderInfo
import com.sbrf.lt.platform.ui.config.defaultUiSecretProvider
import com.sbrf.lt.platform.ui.config.requireSecretKey
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceConnectionStatusResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleEditableSourceGroupRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleEditableSourceGroupResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleEditableSourceRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleEditableSourceResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionsTestRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionsTestResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionTestRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionTestResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsCredentialsDiagnosticsRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsCredentialsDiagnosticsResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsFilePickRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsFilePickResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSecretProviderResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.readText

internal interface UiSqlConsoleSourceSettingsOperations {
    fun loadSettings(uiConfig: UiAppConfig): SqlConsoleSourceSettingsResponse

    fun saveSettings(
        request: SqlConsoleSourceSettingsUpdateRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsResponse

    fun testConnection(
        request: SqlConsoleSourceSettingsConnectionTestRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsConnectionTestResponse

    fun testConnections(
        request: SqlConsoleSourceSettingsConnectionsTestRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsConnectionsTestResponse

    fun diagnoseCredentials(
        request: SqlConsoleSourceSettingsCredentialsDiagnosticsRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsCredentialsDiagnosticsResponse

    fun pickCredentialsFile(
        request: SqlConsoleSourceSettingsFilePickRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsFilePickResponse
}

internal open class UiSqlConsoleSourceSettingsService(
    private val uiConfigPersistenceService: UiConfigPersistenceService,
    private val runtimeConfigResolver: UiRuntimeConfigResolver,
    private val sqlConsoleService: SqlConsoleOperations,
    private val filePicker: UiSqlConsoleSourceSettingsFilePickerOperations =
        DesktopUiSqlConsoleSourceSettingsFilePicker(),
    private val secretProvider: UiSecretProvider = defaultUiSecretProvider(),
) : UiSqlConsoleSourceSettingsOperations {
    override fun loadSettings(uiConfig: UiAppConfig): SqlConsoleSourceSettingsResponse =
        SqlConsoleSourceSettingsResponse(
            editableConfigPath = uiConfigPersistenceService.resolveEditableConfigPath()?.toString(),
            defaultCredentialsFile = uiConfig.defaultCredentialsFile.orEmpty(),
            secretProvider = secretProvider.providerInfo().toResponse(),
            sources = uiConfig.sqlConsole.sourceCatalog.map { it.toEditableResponse() },
            groups = uiConfig.sqlConsole.groups.map { it.toEditableResponse() },
        )

    override fun saveSettings(
        request: SqlConsoleSourceSettingsUpdateRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsResponse {
        val configBaseDir = currentUiConfig.configBaseDir?.let { Path.of(it) }
        validateDefaultCredentialsFile(request.defaultCredentialsFile, configBaseDir)
        val sourceCatalog = request.toSourceCatalog(
            currentUiConfig = currentUiConfig,
            mode = SourceMaterializationMode.PERSIST,
        )
        val groups = request.toSourceGroups(sourceCatalog.map { it.name.trim() }.toSet())
        validateSqlConsoleConfig(
            currentUiConfig.sqlConsole.copy(
                sourceCatalog = sourceCatalog,
                groups = groups,
            ),
        )
        val updated = uiConfigPersistenceService.updateSqlConsoleSourceCatalog(
            sourceCatalog = sourceCatalog,
            groups = groups,
            defaultCredentialsFile = request.defaultCredentialsFile,
        )
        val runtimeUpdated = runtimeConfigResolver.resolve(updated)
        sqlConsoleService.updateConfig(runtimeUpdated.sqlConsole)
        return loadSettings(updated)
    }

    override fun testConnection(
        request: SqlConsoleSourceSettingsConnectionTestRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsConnectionTestResponse {
        validateDefaultCredentialsFile(
            request.defaultCredentialsFile,
            currentUiConfig.configBaseDir?.let { Path.of(it) },
        )
        val source = request.source.toSourceConfig(currentUiConfig, SourceMaterializationMode.RUNTIME_CHECK)
        validateSource(source)
        val runtimeConfig = draftRuntimeConfigResolver().resolve(
            currentUiConfig.copy(
                defaultCredentialsFile = request.defaultCredentialsFile.trim().takeIf { it.isNotEmpty() },
                sqlConsole = currentUiConfig.sqlConsole.copy(
                    sourceCatalog = listOf(source),
                    groups = emptyList(),
                ),
            ),
        )
        val result = SqlConsoleService(runtimeConfig.sqlConsole)
            .checkConnections(credentialsPath = null)
            .sourceResults
            .firstOrNull()
        val status = result?.status.orEmpty()
        val success = status.equals("OK", ignoreCase = true)
        val detail = result?.message?.takeIf { it.isNotBlank() }
            ?: result?.errorMessage?.takeIf { it.isNotBlank() }
        return SqlConsoleSourceSettingsConnectionTestResponse(
            success = success,
            sourceName = source.name,
            message = if (success) {
                "Подключение к source '${source.name}' успешно.${detail?.let { " $it" }.orEmpty()}"
            } else {
                "Не удалось подключиться к source '${source.name}'.${detail?.let { " $it" }.orEmpty()}"
            },
        )
    }

    override fun testConnections(
        request: SqlConsoleSourceSettingsConnectionsTestRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsConnectionsTestResponse {
        validateDefaultCredentialsFile(
            request.settings.defaultCredentialsFile,
            currentUiConfig.configBaseDir?.let { Path.of(it) },
        )
        val sourceCatalog = request.settings.toSourceCatalog(
            currentUiConfig = currentUiConfig,
            mode = SourceMaterializationMode.RUNTIME_CHECK,
        )
        val runtimeConfig = draftRuntimeConfigResolver().resolve(
            currentUiConfig.copy(
                defaultCredentialsFile = request.settings.defaultCredentialsFile.trim().takeIf { it.isNotEmpty() },
                sqlConsole = currentUiConfig.sqlConsole.copy(
                    sourceCatalog = sourceCatalog,
                    groups = emptyList(),
                ),
            ),
        )
        val sourceResults = SqlConsoleService(runtimeConfig.sqlConsole)
            .checkConnections(credentialsPath = null)
            .sourceResults
            .map {
                SqlConsoleSourceConnectionStatusResponse(
                    sourceName = it.shardName,
                    status = it.status,
                    message = it.message,
                    errorMessage = it.errorMessage,
                )
            }
        val failedCount = sourceResults.count { it.status.equals("FAILED", ignoreCase = true) }
        val okCount = sourceResults.count { it.status.equals("OK", ignoreCase = true) }
        return SqlConsoleSourceSettingsConnectionsTestResponse(
            success = failedCount == 0,
            message = if (failedCount == 0) {
                "Проверка sources завершена: $okCount OK."
            } else {
                "Проверка sources завершена: $okCount OK, $failedCount ошибка."
            },
            sourceResults = sourceResults,
        )
    }

    override fun diagnoseCredentials(
        request: SqlConsoleSourceSettingsCredentialsDiagnosticsRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsCredentialsDiagnosticsResponse {
        val requiredKeys = request.settings.extractRequiredPlaceholderKeys(currentUiConfig)
        val configuredPath = request.settings.defaultCredentialsFile.trim()
        val resolvedPath = resolveSqlConsoleSettingsPath(
            rawValue = configuredPath,
            configBaseDir = currentUiConfig.configBaseDir?.let { Path.of(it) },
        )
        val properties = resolvedPath
            ?.takeIf { Files.exists(it) }
            ?.let(::readCredentialsProperties)
            ?: emptyMap()
        val availableKeys = properties.keys.sorted()
        val missingKeys = requiredKeys.filter { it !in properties.keys }
        val fileAvailable = resolvedPath != null && Files.exists(resolvedPath)
        return SqlConsoleSourceSettingsCredentialsDiagnosticsResponse(
            configuredPath = configuredPath,
            resolvedPath = resolvedPath?.toString(),
            fileAvailable = fileAvailable,
            requiredKeys = requiredKeys,
            availableKeys = availableKeys,
            missingKeys = missingKeys,
            message = buildCredentialsDiagnosticsMessage(
                configuredPath = configuredPath,
                fileAvailable = fileAvailable,
                requiredKeys = requiredKeys,
                missingKeys = missingKeys,
            ),
        )
    }

    private fun SqlConsoleSourceSettingsUpdateRequest.extractRequiredPlaceholderKeys(
        currentUiConfig: UiAppConfig,
    ): List<String> {
        val existingSources = currentUiConfig.sqlConsole.sourceCatalog.associateBy { it.name.trim() }
        return sources.flatMap { source ->
            val originalSource = existingSources[source.originalName.trim()]
            val password = if (source.credentialsMode.normalizedCredentialsMode() == CREDENTIALS_MODE_SYSTEM_KEYCHAIN) {
                ""
            } else {
                source.passwordReference.trim().ifBlank {
                    if (source.keepExistingPassword) {
                        originalSource?.password?.trim().orEmpty()
                    } else {
                        ""
                    }
                }
            }
            listOf(source.jdbcUrl, source.username, password).mapNotNull(::extractPropertyPlaceholderKey)
        }.distinct().sorted()
    }

    override fun pickCredentialsFile(
        request: SqlConsoleSourceSettingsFilePickRequest,
        currentUiConfig: UiAppConfig,
    ): SqlConsoleSourceSettingsFilePickResponse =
        filePicker.pickCredentialsFile(
            currentValue = request.currentValue,
            configBaseDir = currentUiConfig.configBaseDir?.let { Path.of(it) },
        )

    private fun SqlConsoleSourceSettingsUpdateRequest.toSourceCatalog(
        currentUiConfig: UiAppConfig,
        mode: SourceMaterializationMode,
    ): List<SqlConsoleSourceConfig> {
        require(sources.isNotEmpty()) {
            "В SQL-консоли должен быть настроен хотя бы один source."
        }
        val result = sources.map { it.toSourceConfig(currentUiConfig, mode) }
        validateSourceCatalog(result)
        return result
    }

    private fun SqlConsoleEditableSourceRequest.toSourceConfig(
        currentUiConfig: UiAppConfig,
        mode: SourceMaterializationMode,
    ): SqlConsoleSourceConfig {
        val existingSources = currentUiConfig.sqlConsole.sourceCatalog.associateBy { it.name.trim() }
        val originalSource = existingSources[originalName.trim()]
        val password = materializePassword(originalSource, mode)
        return SqlConsoleSourceConfig(
            name = name.trim(),
            jdbcUrl = jdbcUrl.trim(),
            username = username.trim(),
            password = password,
        )
    }

    private fun SqlConsoleEditableSourceRequest.materializePassword(
        originalSource: SqlConsoleSourceConfig?,
        mode: SourceMaterializationMode,
    ): String =
        when (credentialsMode.normalizedCredentialsMode()) {
            CREDENTIALS_MODE_SYSTEM_KEYCHAIN -> materializeSystemKeychainPassword(originalSource, mode)
            else -> materializePlaceholderPassword(originalSource)
        }

    private fun SqlConsoleEditableSourceRequest.materializePlaceholderPassword(
        originalSource: SqlConsoleSourceConfig?,
    ): String =
        passwordReference.trim().ifBlank {
            if (keepExistingPassword) {
                originalSource?.password?.trim().orEmpty()
            } else {
                ""
            }
        }

    private fun SqlConsoleEditableSourceRequest.materializeSystemKeychainPassword(
        originalSource: SqlConsoleSourceConfig?,
        mode: SourceMaterializationMode,
    ): String {
        val key = secretKey.trim().ifBlank { buildSqlConsoleSecretKey(name) }
        requireSecretKey(key)
        val plaintext = passwordPlainText
        val existingSecretRef = originalSource?.password?.trim()?.extractSecretKey()?.takeIf { it == key }
        if (mode == SourceMaterializationMode.RUNTIME_CHECK && plaintext.isNotBlank()) {
            return plaintext
        }
        if (plaintext.isNotBlank()) {
            val providerInfo = secretProvider.providerInfo()
            check(providerInfo.available) {
                providerInfo.unavailableReason ?: "System keychain недоступен."
            }
            secretProvider.saveSecret(key, plaintext)
        } else {
            require(existingSecretRef != null && keepExistingPassword) {
                "Password для source '${name.trim()}' должен быть задан для сохранения в System keychain."
            }
        }
        return "\${secret:$key}"
    }

    private fun SqlConsoleSourceSettingsUpdateRequest.toSourceGroups(
        knownSourceNames: Set<String>,
    ): List<SqlConsoleSourceGroupConfig> {
        val result = groups.map { it.toSourceGroupConfig(knownSourceNames) }
        val duplicates = result
            .map { it.name.trim() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Имена групп SQL-консоли должны быть уникальны: ${duplicates.joinToString(", ")}"
        }
        return result
    }

    private fun SqlConsoleEditableSourceGroupRequest.toSourceGroupConfig(
        knownSourceNames: Set<String>,
    ): SqlConsoleSourceGroupConfig {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) {
            "Имя группы SQL-консоли не должно быть пустым."
        }
        val normalizedSources = sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        require(normalizedSources.isNotEmpty()) {
            "Группа SQL-консоли '$normalizedName' должна содержать хотя бы один source."
        }
        val unknown = normalizedSources.filter { it !in knownSourceNames }
        require(unknown.isEmpty()) {
            "Группа SQL-консоли '$normalizedName' содержит неизвестные source: ${unknown.joinToString(", ")}"
        }
        return SqlConsoleSourceGroupConfig(
            name = normalizedName,
            sources = normalizedSources,
        )
    }

    private fun validateSourceCatalog(sources: List<SqlConsoleSourceConfig>) {
        sources.forEach(::validateSource)
        val duplicates = sources
            .map { it.name.trim() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Имена source должны быть уникальны: ${duplicates.joinToString(", ")}"
        }
    }

    private fun validateSource(source: SqlConsoleSourceConfig) {
        require(source.name.isNotBlank()) { "Имя source не должно быть пустым." }
        require(source.jdbcUrl.isNotBlank()) { "jdbcUrl для source '${source.name}' не должен быть пустым." }
        require(source.username.isNotBlank()) { "username для source '${source.name}' не должен быть пустым." }
        require(source.password.isNotBlank()) {
            "password для source '${source.name}' должен быть задан через placeholder или System keychain."
        }
    }

    private fun validateSqlConsoleConfig(config: SqlConsoleConfig) {
        require(config.maxRowsPerShard > 0) { "Лимит строк на source должен быть больше 0." }
        val queryTimeoutSec = config.queryTimeoutSec
        require(queryTimeoutSec == null || queryTimeoutSec > 0) {
            "Таймаут запроса на source должен быть больше 0, если задан."
        }
    }

    private fun validateDefaultCredentialsFile(
        rawValue: String,
        configBaseDir: Path?,
    ) {
        val resolvedPath = resolveSqlConsoleSettingsPath(rawValue, configBaseDir) ?: return
        require(Files.exists(resolvedPath)) {
            "credential.properties не найден: $resolvedPath"
        }
    }

    private fun draftRuntimeConfigResolver(): UiRuntimeConfigResolver =
        UiRuntimeConfigResolver(secretProvider = secretProvider)
}

private enum class SourceMaterializationMode {
    PERSIST,
    RUNTIME_CHECK,
}

private fun readCredentialsProperties(path: Path): Map<String, String> {
    val props = Properties()
    props.load(path.readText().removePrefix("\uFEFF").reader())
    return props.stringPropertyNames().associateWith { props.getProperty(it) }
}

private fun extractPropertyPlaceholderKey(rawValue: String): String? {
    val match = PROPERTY_PLACEHOLDER_PATTERN.matchEntire(rawValue.trim()) ?: return null
    return match.groupValues[1]
}

private fun buildCredentialsDiagnosticsMessage(
    configuredPath: String,
    fileAvailable: Boolean,
    requiredKeys: List<String>,
    missingKeys: List<String>,
): String =
    when {
        configuredPath.isBlank() && requiredKeys.isEmpty() ->
            "credential.properties не задан, placeholder keys не требуются."
        configuredPath.isBlank() ->
            "credential.properties не задан. Требуются keys: ${requiredKeys.joinToString(", ")}."
        !fileAvailable ->
            "credential.properties не найден."
        missingKeys.isEmpty() ->
            "credential.properties доступен, все required keys найдены."
        else ->
            "credential.properties доступен, но не хватает keys: ${missingKeys.joinToString(", ")}."
    }

private fun SqlConsoleSourceConfig.toEditableResponse(): SqlConsoleEditableSourceResponse {
    val secretKey = password.extractSecretKey().orEmpty()
    val credentialsMode = if (secretKey.isNotBlank()) {
        CREDENTIALS_MODE_SYSTEM_KEYCHAIN
    } else {
        CREDENTIALS_MODE_PLACEHOLDERS
    }
    return SqlConsoleEditableSourceResponse(
        originalName = name.trim(),
        name = name.trim(),
        credentialsMode = credentialsMode,
        jdbcUrl = jdbcUrl,
        username = username,
        passwordReference = if (credentialsMode == CREDENTIALS_MODE_PLACEHOLDERS) {
            password.takeIf { it.isPlaceholderReference() }.orEmpty()
        } else {
            ""
        },
        passwordConfigured = password.isNotBlank(),
        secretKey = secretKey.ifBlank { buildSqlConsoleSecretKey(name) },
        secretConfigured = secretKey.isNotBlank(),
        passwordPlainText = "",
    )
}

private fun SqlConsoleSourceGroupConfig.toEditableResponse(): SqlConsoleEditableSourceGroupResponse =
    SqlConsoleEditableSourceGroupResponse(
        originalName = name.trim(),
        name = name.trim(),
        sources = sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    )

private fun String.isPlaceholderReference(): Boolean {
    val trimmed = trim()
    return PROPERTY_PLACEHOLDER_PATTERN.matches(trimmed)
}

private val PROPERTY_PLACEHOLDER_PATTERN = Regex("^\\$\\{([A-Za-z0-9_.-]+)}$")
private val SECRET_PLACEHOLDER_PATTERN = Regex("^\\$\\{secret:([A-Za-z0-9_.-]+)}$")

private const val CREDENTIALS_MODE_PLACEHOLDERS = "PLACEHOLDERS"
private const val CREDENTIALS_MODE_SYSTEM_KEYCHAIN = "SYSTEM_KEYCHAIN"

private fun String.normalizedCredentialsMode(): String =
    trim().uppercase().takeIf { it == CREDENTIALS_MODE_SYSTEM_KEYCHAIN }
        ?: CREDENTIALS_MODE_PLACEHOLDERS

private fun String.extractSecretKey(): String? =
    SECRET_PLACEHOLDER_PATTERN.matchEntire(trim())?.groupValues?.get(1)

private fun buildSqlConsoleSecretKey(sourceName: String): String {
    val slug = sourceName
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-', '_', '.')
        .ifBlank { "source" }
    return "sqlConsole.sources.$slug.password"
}

private fun UiSecretProviderInfo.toResponse(): SqlConsoleSecretProviderResponse =
    SqlConsoleSecretProviderResponse(
        providerId = providerId,
        displayName = displayName,
        available = available,
        unavailableReason = unavailableReason,
    )

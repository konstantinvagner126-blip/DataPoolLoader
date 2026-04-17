package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.platform.ui.run.UiCredentialsProvider
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.readText
import java.io.StringReader

/**
 * Готовит runtime-копию UI-конфига с best-effort resolution placeholders `${...}`.
 * Исходный YAML не меняется и не должен использоваться для сохранения секретов.
 */
open class UiRuntimeConfigResolver(
    private val credentialsProvider: UiCredentialsProvider? = null,
) {
    open fun resolve(uiConfig: UiAppConfig): UiAppConfig {
        val properties = credentialsProvider?.currentProperties()
            ?.takeIf { it.isNotEmpty() }
            ?: loadFallbackProperties(uiConfig.defaultCredentialsPath())
        return uiConfig.copy(
            moduleStore = uiConfig.moduleStore.copy(
                postgres = uiConfig.moduleStore.postgres.resolvePlaceholders(properties),
            ),
            sqlConsole = uiConfig.sqlConsole.resolvePlaceholders(properties),
        )
    }

    private fun UiModuleStorePostgresConfig.resolvePlaceholders(properties: Map<String, String>): UiModuleStorePostgresConfig =
        copy(
            jdbcUrl = jdbcUrl?.let { resolveIfPossible(it, properties) },
            username = username?.let { resolveIfPossible(it, properties) },
            password = password?.let { resolveIfPossible(it, properties) },
        )

    private fun SqlConsoleConfig.resolvePlaceholders(properties: Map<String, String>): SqlConsoleConfig =
        copy(
            sources = sources.map { it.resolvePlaceholders(properties) },
        )

    private fun SqlConsoleSourceConfig.resolvePlaceholders(properties: Map<String, String>): SqlConsoleSourceConfig =
        copy(
            jdbcUrl = resolveIfPossible(jdbcUrl, properties),
            username = resolveIfPossible(username, properties),
            password = resolveIfPossible(password, properties),
        )

    private fun resolveIfPossible(rawValue: String, properties: Map<String, String>): String {
        val trimmed = rawValue.trim()
        val match = PLACEHOLDER_PATTERN.matchEntire(trimmed) ?: return rawValue
        val key = match.groupValues[1]
        return properties[key]
            ?: System.getenv(key)
            ?: System.getProperty(key)
            ?: rawValue
    }

    private fun loadFallbackProperties(path: Path?): Map<String, String> {
        if (path == null || !path.exists()) {
            return emptyMap()
        }
        return runCatching {
            val props = Properties()
            props.load(StringReader(path.readText().removePrefix("\uFEFF")))
            props.stringPropertyNames().associateWith { props.getProperty(it) }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        val PLACEHOLDER_PATTERN = Regex("^\\$\\{([A-Za-z0-9_.-]+)}$")
    }
}

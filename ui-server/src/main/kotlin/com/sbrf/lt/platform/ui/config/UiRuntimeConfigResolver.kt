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
    private val placeholderResolutionSupport = UiConfigPlaceholderResolutionSupport()
    private val kafkaValidationSupport = UiKafkaConfigValidationSupport()

    open fun resolve(uiConfig: UiAppConfig): UiAppConfig {
        val properties = credentialsProvider?.currentProperties()
            ?.takeIf { it.isNotEmpty() }
            ?: loadFallbackProperties(uiConfig.defaultCredentialsPath())
        val resolvedKafka = uiConfig.kafka.resolvePlaceholders(
            properties = properties,
            baseDir = uiConfig.configBaseDir?.let { Path.of(it) },
        )
        kafkaValidationSupport.validateForRuntime(resolvedKafka)
        return uiConfig.copy(
            moduleStore = uiConfig.moduleStore.copy(
                postgres = uiConfig.moduleStore.postgres.resolvePlaceholders(properties),
            ),
            sqlConsole = uiConfig.sqlConsole.resolvePlaceholders(properties),
            kafka = resolvedKafka,
        )
    }

    private fun UiModuleStorePostgresConfig.resolvePlaceholders(properties: Map<String, String>): UiModuleStorePostgresConfig =
        copy(
            jdbcUrl = jdbcUrl?.let { placeholderResolutionSupport.resolveIfPossible(it, properties) },
            username = username?.let { placeholderResolutionSupport.resolveIfPossible(it, properties) },
            password = password?.let { placeholderResolutionSupport.resolveIfPossible(it, properties) },
        )

    private fun SqlConsoleConfig.resolvePlaceholders(properties: Map<String, String>): SqlConsoleConfig =
        copy(
            sourceCatalog = sourceCatalog.map { it.resolvePlaceholders(properties) },
        )

    private fun SqlConsoleSourceConfig.resolvePlaceholders(properties: Map<String, String>): SqlConsoleSourceConfig =
        copy(
            jdbcUrl = placeholderResolutionSupport.resolveIfPossible(jdbcUrl, properties),
            username = placeholderResolutionSupport.resolveIfPossible(username, properties),
            password = placeholderResolutionSupport.resolveIfPossible(password, properties),
        )

    private fun UiKafkaConfig.resolvePlaceholders(
        properties: Map<String, String>,
        baseDir: Path?,
    ): UiKafkaConfig = copy(
        clusters = clusters.map { it.resolvePlaceholders(properties, baseDir) },
    )

    private fun UiKafkaClusterConfig.resolvePlaceholders(
        properties: Map<String, String>,
        baseDir: Path?,
    ): UiKafkaClusterConfig = copy(
        properties = this.properties.mapValues { (_, value) ->
            placeholderResolutionSupport.resolveIfPossible(value, properties, baseDir)
        },
    )

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
}

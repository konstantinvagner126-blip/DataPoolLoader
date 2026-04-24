package com.sbrf.lt.platform.ui.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

data class UiRootConfig(
    val ui: UiAppConfig = UiAppConfig(),
)

data class UiAppConfig(
    val port: Int = 8080,
    val appsRoot: String? = null,
    val storageDir: String? = null,
    val moduleStore: UiModuleStoreConfig = UiModuleStoreConfig(),
    val outputRetention: UiOutputRetentionConfig = UiOutputRetentionConfig(),
    val showTechnicalDiagnostics: Boolean = true,
    val showRawSummaryJson: Boolean = false,
    val defaultCredentialsFile: String? = null,
    val sqlConsole: SqlConsoleConfig = SqlConsoleConfig(),
    val kafka: UiKafkaConfig = UiKafkaConfig(),
    @JsonIgnore val configBaseDir: String? = null,
)

data class UiOutputRetentionConfig(
    val retentionDays: Int = 14,
    val keepMinRunsPerModule: Int = 20,
)

data class UiModuleStoreConfig(
    val mode: UiModuleStoreMode = UiModuleStoreMode.FILES,
    val postgres: UiModuleStorePostgresConfig = UiModuleStorePostgresConfig(),
)

enum class UiModuleStoreMode(
    private val configValue: String,
) {
    FILES("files"),
    DATABASE("database"),
    ;

    @JsonValue
    fun toConfigValue(): String = configValue

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromConfigValue(rawValue: String): UiModuleStoreMode {
            return entries.firstOrNull { it.configValue.equals(rawValue.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Неизвестный режим moduleStore.mode: $rawValue")
        }
    }
}

data class UiModuleStorePostgresConfig(
    val jdbcUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val schema: String = DEFAULT_SCHEMA,
) {
    companion object {
        const val DEFAULT_SCHEMA = "ui_registry"
    }
}

open class UiConfigLoader(
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val loadSupport = UiConfigLoadSupport(configLoader)
    private val externalPathSupport = UiConfigExternalPathResolutionSupport(
        packagedSidecarPathProvider = ::resolvePackagedSidecarConfigPath,
    )

    open fun load(): UiAppConfig {
        return loadSupport.load(
            externalConfig = resolveExternalConfigPath(),
            classpathStream = classpathConfigStream(),
        )
    }

    open fun resolveExternalConfigPath(): Path? = externalPathSupport.resolveExternalConfigPath()

    open fun resolvePackagedSidecarConfigPath(): Path? {
        return resolveReadablePackagedSidecarConfigPath(resolvePackagedAppDirectory())
    }

    open fun classpathConfigStream(): InputStream? =
        javaClass.classLoader.getResourceAsStream("application.yml")

    open fun resolvePackagedAppDirectory(): Path? {
        return resolvePackagedUiAppDirectory(
            launcherPath = System.getProperty("jpackage.app-path")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { Path.of(it).toAbsolutePath().normalize() },
            processCommand = resolveProcessCommand(),
        )
    }

    open fun resolveProcessCommand(): String? =
        ProcessHandle.current().info().command().orElse(null)
}

package com.sbrf.lt.platform.ui.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.CredentialsFileLocator
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

data class UiRootConfig(
    val ui: UiAppConfig = UiAppConfig(),
)

data class UiAppConfig(
    val port: Int = 8080,
    val appsRoot: String? = null,
    val storageDir: String? = null,
    val showTechnicalDiagnostics: Boolean = true,
    val showRawSummaryJson: Boolean = false,
    val defaultCredentialsFile: String? = null,
    val sqlConsole: SqlConsoleConfig = SqlConsoleConfig(),
    @JsonIgnore val configBaseDir: String? = null,
)

open class UiConfigLoader(
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    open fun load(): UiAppConfig {
        val externalConfig = resolveExternalConfigPath()
        if (externalConfig != null) {
            require(Files.exists(externalConfig)) {
                "UI-конфиг не найден: $externalConfig"
            }
            externalConfig.inputStream().bufferedReader().use {
                return configLoader.objectMapper()
                    .readValue(it, UiRootConfig::class.java)
                    .ui
                    .copy(configBaseDir = externalConfig.parent?.toAbsolutePath()?.normalize()?.toString())
            }
        }

        val stream: InputStream = classpathConfigStream() ?: return UiAppConfig()
        return stream.bufferedReader().use {
            configLoader.objectMapper().readValue(it, UiRootConfig::class.java).ui
        }
    }

    open fun resolveExternalConfigPath(): Path? {
        System.getProperty("datapool.ui.config")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return Path.of(it).toAbsolutePath().normalize() }

        System.getenv("DATAPOOL_UI_CONFIG")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return Path.of(it).toAbsolutePath().normalize() }

        resolvePackagedSidecarConfigPath()?.let { return it }

        val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val defaultPath = Path.of(userHome).resolve(".datapool-loader/ui/application.yml").normalize()
        return defaultPath.takeIf { Files.exists(it) }
    }

    open fun resolvePackagedSidecarConfigPath(): Path? {
        val appDir = resolvePackagedAppDirectory() ?: return null
        val candidates = listOf(
            appDir.resolve("ui-application.yml"),
            appDir.resolve("application.yml"),
        )
        return candidates.firstOrNull { Files.exists(it) }
    }

    open fun classpathConfigStream(): InputStream? =
        javaClass.classLoader.getResourceAsStream("application.yml")

    open fun resolvePackagedAppDirectory(): Path? {
        val launcherPath = System.getProperty("jpackage.app-path")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: resolveProcessCommand()
                ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: return null

        val macAppBundle = launcherPath.ancestors()
            .firstOrNull { it.fileName?.toString()?.endsWith(".app") == true }
        if (macAppBundle != null) {
            return macAppBundle.parent?.normalize()
        }
        return launcherPath.parent?.normalize()
    }

    open fun resolveProcessCommand(): String? =
        ProcessHandle.current().info().command().orElse(null)
}

fun UiAppConfig.defaultCredentialsPath(): Path? {
    val configured = defaultCredentialsFile?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredPath ->
        val path = Path.of(configuredPath)
        if (path.isAbsolute) {
            path
        } else {
            configBaseDir?.let { Path.of(it).resolve(path).normalize() } ?: path
        }
    }
    if (configured != null) {
        return configured
    }
    return CredentialsFileLocator.find()
}

fun UiAppConfig.appsRootPath(): Path? {
    return appsRoot?.trim()?.takeIf { it.isNotEmpty() }?.let {
        Path.of(it).toAbsolutePath().normalize()
    }
}

fun UiAppConfig.storageDirPath(): Path {
    storageDir?.trim()?.takeIf { it.isNotEmpty() }?.let { configuredStorageDir ->
        val path = Path.of(configuredStorageDir)
        if (path.isAbsolute) {
            return path.normalize()
        }
        configBaseDir?.let {
            return Path.of(it).resolve(path).normalize()
        }
        return path.toAbsolutePath().normalize()
    }
    val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }
        ?: return Path.of(".datapool-loader/ui/storage").toAbsolutePath().normalize()
    return Path.of(userHome).resolve(".datapool-loader/ui/storage").toAbsolutePath().normalize()
}

private fun Path.ancestors(): Sequence<Path> = sequence {
    var current: Path? = this@ancestors
    while (current != null) {
        yield(current)
        current = current.parent
    }
}

package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.ProjectRootLocator
import java.nio.file.Files
import java.nio.file.Path

open class UiConfigPersistenceService(
    private val uiConfigLoader: UiConfigLoader = UiConfigLoader(),
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val startDir: Path = Path.of("").toAbsolutePath().normalize(),
) {
    open fun resolveEditableConfigPath(): Path? {
        uiConfigLoader.resolveExternalConfigPath()?.let { return it }

        val projectRoot = ProjectRootLocator.find(startDir) ?: return null
        val devConfig = projectRoot.resolve("ui/src/main/resources/application.yml").normalize()
        return devConfig.takeIf { Files.exists(it) }
    }

    open fun resolveManagedExternalConfigPath(): Path {
        resolveExplicitExternalConfigPath()?.let { return it }
        uiConfigLoader.resolveExternalConfigPath()?.let { return it }
        resolvePackagedSidecarConfigPath()?.let { return it }

        val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }
        return if (userHome != null) {
            Path.of(userHome).resolve(".datapool-loader/ui/application.yml").toAbsolutePath().normalize()
        } else {
            Path.of(".datapool-loader/ui/application.yml").toAbsolutePath().normalize()
        }
    }

    open fun updateModuleStoreMode(mode: UiModuleStoreMode): UiAppConfig {
        val targetPath = resolveManagedExternalConfigPath()
        val baseUiConfig = readBaseUiConfig(targetPath)
        val updatedUiConfig = baseUiConfig.copy(
            moduleStore = baseUiConfig.moduleStore.copy(mode = mode),
        )
        return writeUiConfig(targetPath, updatedUiConfig)
    }

    open fun updateSqlConsoleSettings(
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): UiAppConfig {
        require(maxRowsPerShard > 0) { "Лимит строк на source должен быть больше 0." }
        require(queryTimeoutSec == null || queryTimeoutSec > 0) {
            "Таймаут запроса на source должен быть больше 0, если задан."
        }

        val targetPath = requireNotNull(resolveEditableConfigPath()) {
            "Не удалось определить редактируемый ui-конфиг. Укажи внешний ui-application.yml или запусти UI из проекта."
        }

        val baseUiConfig = readBaseUiConfig(targetPath)

        val updatedUiConfig = baseUiConfig.copy(
            sqlConsole = baseUiConfig.sqlConsole.copy(
                maxRowsPerShard = maxRowsPerShard,
                queryTimeoutSec = queryTimeoutSec,
            ),
        )

        return writeUiConfig(targetPath, updatedUiConfig)
    }

    open fun updateSqlConsoleMaxRowsPerShard(maxRowsPerShard: Int): UiAppConfig {
        val currentTimeout = uiConfigLoader.load().sqlConsole.queryTimeoutSec
        return updateSqlConsoleSettings(maxRowsPerShard, currentTimeout)
    }

    private fun readBaseUiConfig(targetPath: Path): UiAppConfig {
        return if (Files.exists(targetPath)) {
            Files.newBufferedReader(targetPath).use {
                configLoader.objectMapper().readValue(it, UiRootConfig::class.java).ui
            }
        } else {
            uiConfigLoader.load()
        }
    }

    private fun writeUiConfig(
        targetPath: Path,
        updatedUiConfig: UiAppConfig,
    ): UiAppConfig {
        targetPath.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(targetPath).use { writer ->
            configLoader.objectMapper().writeValue(writer, UiRootConfig(updatedUiConfig))
        }

        return updatedUiConfig.copy(
            configBaseDir = targetPath.parent?.toAbsolutePath()?.normalize()?.toString(),
        )
    }

    private fun resolveExplicitExternalConfigPath(): Path? {
        System.getProperty("datapool.ui.config")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return Path.of(it).toAbsolutePath().normalize() }

        System.getenv("DATAPOOL_UI_CONFIG")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return Path.of(it).toAbsolutePath().normalize() }

        return null
    }

    private fun resolvePackagedSidecarConfigPath(): Path? {
        val appPath = System.getProperty("jpackage.app-path")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val launcherPath = Path.of(appPath).toAbsolutePath().normalize()
        val macAppBundle = generateSequence(launcherPath) { it.parent }
            .firstOrNull { it.fileName?.toString()?.endsWith(".app") == true }
        val appDir = macAppBundle?.parent ?: launcherPath.parent ?: return null
        return appDir.resolve("ui-application.yml").normalize()
    }
}

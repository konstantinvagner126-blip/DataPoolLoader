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

        val baseUiConfig = if (Files.exists(targetPath)) {
            Files.newBufferedReader(targetPath).use {
                configLoader.objectMapper().readValue(it, UiRootConfig::class.java).ui
            }
        } else {
            uiConfigLoader.load()
        }

        val updatedUiConfig = baseUiConfig.copy(
            sqlConsole = baseUiConfig.sqlConsole.copy(
                maxRowsPerShard = maxRowsPerShard,
                queryTimeoutSec = queryTimeoutSec,
            ),
        )

        targetPath.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(targetPath).use { writer ->
            configLoader.objectMapper().writeValue(writer, UiRootConfig(updatedUiConfig))
        }

        return updatedUiConfig.copy(
            configBaseDir = targetPath.parent?.toAbsolutePath()?.normalize()?.toString(),
        )
    }

    open fun updateSqlConsoleMaxRowsPerShard(maxRowsPerShard: Int): UiAppConfig {
        val currentTimeout = uiConfigLoader.load().sqlConsole.queryTimeoutSec
        return updateSqlConsoleSettings(maxRowsPerShard, currentTimeout)
    }
}

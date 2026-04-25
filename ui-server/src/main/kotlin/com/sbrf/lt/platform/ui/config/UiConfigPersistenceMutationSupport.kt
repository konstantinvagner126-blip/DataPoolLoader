package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import java.nio.file.Files
import java.nio.file.Path

internal class UiConfigPersistenceMutationSupport(
    private val uiConfigLoader: UiConfigLoader,
    private val configLoader: ConfigLoader,
) {
    fun updateModuleStoreMode(targetPath: Path, mode: UiModuleStoreMode): UiAppConfig {
        val baseUiConfig = readBaseUiConfig(targetPath)
        val updatedUiConfig = baseUiConfig.copy(
            moduleStore = baseUiConfig.moduleStore.copy(mode = mode),
        )
        return writeUiConfig(targetPath, updatedUiConfig)
    }

    fun updateSqlConsoleSettings(
        targetPath: Path,
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): UiAppConfig {
        val baseUiConfig = readBaseUiConfig(targetPath)
        val updatedUiConfig = baseUiConfig.copy(
            sqlConsole = baseUiConfig.sqlConsole.copy(
                maxRowsPerShard = maxRowsPerShard,
                queryTimeoutSec = queryTimeoutSec,
            ),
        )
        return writeUiConfig(targetPath, updatedUiConfig)
    }

    fun updateSqlConsoleSourceCatalog(
        targetPath: Path,
        sourceCatalog: List<SqlConsoleSourceConfig>,
        groups: List<SqlConsoleSourceGroupConfig>,
        defaultCredentialsFile: String?,
    ): UiAppConfig {
        val baseUiConfig = readBaseUiConfig(targetPath)
        val updatedUiConfig = baseUiConfig.copy(
            defaultCredentialsFile = defaultCredentialsFile?.trim()?.takeIf { it.isNotEmpty() },
            sqlConsole = baseUiConfig.sqlConsole.copy(
                sourceCatalog = sourceCatalog,
                groups = groups,
            ),
        )
        return writeUiConfig(targetPath, updatedUiConfig)
    }

    fun updateKafkaClusterCatalog(
        targetPath: Path,
        clusters: List<UiKafkaClusterConfig>,
    ): UiAppConfig {
        val baseUiConfig = readBaseUiConfig(targetPath)
        val updatedUiConfig = baseUiConfig.copy(
            kafka = baseUiConfig.kafka.copy(
                clusters = clusters,
            ),
        )
        return writeUiConfig(targetPath, updatedUiConfig)
    }

    private fun readBaseUiConfig(targetPath: Path): UiAppConfig {
        return if (Files.exists(targetPath)) {
            readUiConfigFile(targetPath, configLoader)
        } else {
            uiConfigLoader.load()
        }
    }

    private fun writeUiConfig(
        targetPath: Path,
        updatedUiConfig: UiAppConfig,
    ): UiAppConfig {
        writeUiConfigFile(targetPath, updatedUiConfig, configLoader)
        return updatedUiConfig.copy(
            configBaseDir = targetPath.parent?.toAbsolutePath()?.normalize()?.toString(),
        )
    }
}

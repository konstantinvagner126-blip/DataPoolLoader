package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import java.nio.file.Path

open class UiConfigPersistenceService(
    private val uiConfigLoader: UiConfigLoader = UiConfigLoader(),
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val startDir: Path = Path.of("").toAbsolutePath().normalize(),
) {
    private val pathSupport = UiConfigPersistencePathSupport(uiConfigLoader, startDir)
    private val mutationSupport = UiConfigPersistenceMutationSupport(uiConfigLoader, configLoader)

    open fun resolveEditableConfigPath(): Path? = pathSupport.resolveEditableConfigPath()

    open fun resolveManagedExternalConfigPath(): Path {
        resolveExplicitExternalConfigPath()?.let { return it }
        return pathSupport.resolveManagedExternalConfigPath(
            packagedSidecarPath = resolvePackagedSidecarConfigPath(),
        )
    }

    open fun updateModuleStoreMode(mode: UiModuleStoreMode): UiAppConfig {
        return mutationSupport.updateModuleStoreMode(resolveManagedExternalConfigPath(), mode)
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
        return mutationSupport.updateSqlConsoleSettings(targetPath, maxRowsPerShard, queryTimeoutSec)
    }

    open fun updateSqlConsoleMaxRowsPerShard(maxRowsPerShard: Int): UiAppConfig {
        val currentTimeout = uiConfigLoader.load().sqlConsole.queryTimeoutSec
        return updateSqlConsoleSettings(maxRowsPerShard, currentTimeout)
    }

    open fun updateSqlConsoleSourceCatalog(
        sourceCatalog: List<SqlConsoleSourceConfig>,
        groups: List<SqlConsoleSourceGroupConfig>,
        defaultCredentialsFile: String?,
    ): UiAppConfig {
        val targetPath = requireNotNull(resolveEditableConfigPath()) {
            "Не удалось определить редактируемый ui-конфиг. Укажи внешний ui-application.yml или запусти UI из проекта."
        }
        return mutationSupport.updateSqlConsoleSourceCatalog(
            targetPath = targetPath,
            sourceCatalog = sourceCatalog,
            groups = groups,
            defaultCredentialsFile = defaultCredentialsFile,
        )
    }

    open fun updateKafkaClusterCatalog(
        clusters: List<UiKafkaClusterConfig>,
    ): UiAppConfig {
        val targetPath = requireNotNull(resolveEditableConfigPath()) {
            "Не удалось определить редактируемый ui-конфиг. Укажи внешний ui-application.yml или запусти UI из проекта."
        }
        return mutationSupport.updateKafkaClusterCatalog(targetPath, clusters)
    }

    private fun resolveExplicitExternalConfigPath(): Path? {
        return resolveExplicitUiConfigPath()
    }

    private fun resolvePackagedSidecarConfigPath(): Path? {
        return resolveManagedPackagedSidecarConfigPath(
            resolvePackagedUiAppDirectory(
                launcherPath = System.getProperty("jpackage.app-path")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { Path.of(it).toAbsolutePath().normalize() },
                processCommand = null,
            ),
        )
    }
}

package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaMessageOperations
import com.sbrf.lt.datapool.kafka.KafkaProduceOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.kafka.ConfigBackedKafkaMetadataService
import com.sbrf.lt.platform.ui.kafka.ConfigBackedKafkaMessageService
import com.sbrf.lt.platform.ui.kafka.ConfigBackedKafkaProduceService
import com.sbrf.lt.platform.ui.kafka.UiKafkaSettingsOperations
import com.sbrf.lt.platform.ui.kafka.UiKafkaSettingsService
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.module.backend.FilesModuleBackend
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.FilesModuleRunHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleWorkspaceRetentionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal fun defaultUiConfigLoader(uiConfig: UiAppConfig): UiConfigLoader =
    object : UiConfigLoader() {
        override fun load(): UiAppConfig = uiConfig
    }

internal fun defaultUiCredentialsService(
    uiConfig: UiAppConfig,
    uiConfigLoader: UiConfigLoader,
): UiCredentialsService =
    UiCredentialsService(
        uiConfigProvider = { runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig) },
    )

internal fun defaultModuleRegistry(uiConfig: UiAppConfig): ModuleRegistry =
    ModuleRegistry(appsRoot = uiConfig.appsRootPath())

internal fun defaultFilesModuleBackend(moduleRegistry: ModuleRegistry): FilesModuleBackend =
    FilesModuleBackend(moduleRegistry)

internal fun defaultRunManager(
    moduleRegistry: ModuleRegistry,
    uiConfig: UiAppConfig,
    credentialsService: UiCredentialsService,
): RunManager =
    RunManager(moduleRegistry = moduleRegistry, uiConfig = uiConfig, credentialsService = credentialsService)

internal fun defaultSqlConsoleService(runtimeUiConfig: UiAppConfig): SqlConsoleOperations =
    SqlConsoleService(runtimeUiConfig.sqlConsole)

internal fun defaultSqlConsoleWorkspaceRetentionService(uiConfig: UiAppConfig): SqlConsoleWorkspaceRetentionService =
    SqlConsoleWorkspaceRetentionService(uiConfig.storageDirPath())

internal fun defaultSqlConsoleExecutionHistoryService(
    uiConfig: UiAppConfig,
    workspaceRetentionService: SqlConsoleWorkspaceRetentionService,
): SqlConsoleExecutionHistoryService =
    SqlConsoleExecutionHistoryService(
        stateStore = com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryStateStore(uiConfig.storageDirPath()),
        workspaceRetentionService = workspaceRetentionService,
    )

internal fun defaultSqlConsoleQueryManager(
    sqlConsoleService: SqlConsoleOperations,
    executionHistoryService: SqlConsoleExecutionHistoryService,
): SqlConsoleAsyncQueryOperations =
    SqlConsoleQueryManager(
        sqlConsoleService = sqlConsoleService,
        executionHistoryService = executionHistoryService,
    )

internal fun defaultSqlConsoleStateService(
    uiConfig: UiAppConfig,
    workspaceRetentionService: SqlConsoleWorkspaceRetentionService,
): SqlConsoleStateService =
    SqlConsoleStateService(
        workspaceStore = com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleWorkspaceStateStore(uiConfig.storageDirPath()),
        libraryStore = com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleLibraryStateStore(uiConfig.storageDirPath()),
        preferencesStore = com.sbrf.lt.platform.ui.sqlconsole.SqlConsolePreferencesStateStore(uiConfig.storageDirPath()),
        workspaceRetentionService = workspaceRetentionService,
    )

internal fun defaultKafkaMetadataService(runtimeUiConfig: UiAppConfig): KafkaMetadataOperations =
    ConfigBackedKafkaMetadataService(runtimeUiConfig.kafka)

internal fun defaultKafkaMessageService(runtimeUiConfig: UiAppConfig): KafkaMessageOperations =
    ConfigBackedKafkaMessageService(runtimeUiConfig.kafka)

internal fun defaultKafkaProduceService(runtimeUiConfig: UiAppConfig): KafkaProduceOperations =
    ConfigBackedKafkaProduceService(runtimeUiConfig.kafka)

internal fun defaultKafkaSettingsService(
    uiConfigPersistenceService: UiConfigPersistenceService,
    runtimeConfigResolver: UiRuntimeConfigResolver,
): UiKafkaSettingsOperations =
    UiKafkaSettingsService(
        uiConfigPersistenceService = uiConfigPersistenceService,
        runtimeConfigResolver = runtimeConfigResolver,
    )

internal fun defaultFilesRunHistoryService(
    moduleRegistry: ModuleRegistry,
    runManager: RunManager,
) = FilesModuleRunHistoryService(moduleRegistry, runManager)

internal fun defaultUiServerLogger(): Logger = LoggerFactory.getLogger("UiServerHttp")

internal fun defaultUiServerStartupLogger(): Logger = LoggerFactory.getLogger("com.sbrf.lt.platform.ui.Startup")

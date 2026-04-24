package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.kafka.UiKafkaSettingsOperations
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.module.backend.FilesModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleActiveRunRegistry
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.datapool.kafka.KafkaMetadataOperations
import com.sbrf.lt.datapool.kafka.KafkaMessageOperations
import com.sbrf.lt.datapool.kafka.KafkaProduceOperations

internal data class UiServerContextDependencies(
    val uiConfig: UiAppConfig,
    val uiConfigLoader: UiConfigLoader,
    val credentialsService: UiCredentialsService,
    val runtimeConfigResolver: UiRuntimeConfigResolver,
    val runtimeContextService: UiRuntimeContextService,
    val runtimeUiConfig: UiAppConfig,
    val runtimeContext: UiRuntimeContext,
    val filesModuleBackend: FilesModuleBackend,
    val databaseModuleStore: DatabaseModuleRegistryOperations?,
    val databaseModuleBackend: DatabaseModuleBackend?,
    val runManager: RunManager,
    val configFormService: ConfigFormService,
    val sqlConsoleService: SqlConsoleOperations,
    val sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations,
    val sqlConsoleExportService: SqlConsoleExportService,
    val sqlConsoleExecutionHistoryService: SqlConsoleExecutionHistoryService,
    val sqlConsoleStateService: SqlConsoleStateService,
    val kafkaMetadataService: KafkaMetadataOperations,
    val kafkaMessageService: KafkaMessageOperations,
    val kafkaProduceService: KafkaProduceOperations,
    val kafkaSettingsService: UiKafkaSettingsOperations,
    val uiConfigPersistenceService: UiConfigPersistenceService,
    val moduleSyncService: ModuleSyncService?,
    val databaseModuleRunService: DatabaseModuleRunOperations?,
    val databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry,
    val databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService?,
    val databaseOutputRetentionService: DatabaseOutputRetentionService?,
    val filesModuleRunHistoryService: ModuleRunHistoryService,
    val databaseModuleRunHistoryService: ModuleRunHistoryService?,
)

package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.module.backend.FilesModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleActiveRunRegistry
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.FilesModuleRunOperations
import com.sbrf.lt.platform.ui.run.FilesRunHistoryMaintenanceOperations
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations

/**
 * Общий runtime-контекст Ktor-сервера UI: dynamic config, доступ к сервисам и guard-методы.
 */
internal class UiServerContext(
    internal val uiConfig: UiAppConfig,
    internal val uiConfigLoader: UiConfigLoader,
    internal val credentialsService: UiCredentialsService,
    internal val runtimeConfigResolver: UiRuntimeConfigResolver,
    internal val runtimeContextService: UiRuntimeContextService,
    internal val runtimeUiConfig: UiAppConfig,
    internal val runtimeContext: UiRuntimeContext,
    internal val filesModuleBackend: FilesModuleBackend,
    internal val databaseModuleStore: DatabaseModuleRegistryOperations?,
    internal val databaseModuleBackend: DatabaseModuleBackend?,
    internal val filesRunService: FilesModuleRunOperations,
    internal val filesRunHistoryMaintenance: FilesRunHistoryMaintenanceOperations,
    internal val configFormService: ConfigFormService,
    internal val sqlConsoleService: SqlConsoleOperations,
    internal val sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations,
    internal val sqlConsoleExportService: SqlConsoleExportService,
    internal val sqlConsoleStateService: SqlConsoleStateService,
    internal val uiConfigPersistenceService: UiConfigPersistenceService,
    internal val moduleSyncService: ModuleSyncService?,
    internal val databaseModuleRunService: DatabaseModuleRunOperations?,
    internal val databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry,
    internal val databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService?,
    internal val databaseOutputRetentionService: DatabaseOutputRetentionService?,
    internal val filesModuleRunHistoryService: ModuleRunHistoryService,
    internal val databaseModuleRunHistoryService: ModuleRunHistoryService?,
)

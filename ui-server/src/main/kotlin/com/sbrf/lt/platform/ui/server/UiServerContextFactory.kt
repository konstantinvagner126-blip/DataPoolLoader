package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
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
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService

internal fun buildUiServerContext(
    uiConfig: UiAppConfig,
    uiConfigLoader: UiConfigLoader,
    credentialsService: UiCredentialsService,
    runtimeConfigResolver: UiRuntimeConfigResolver,
    runtimeContextService: UiRuntimeContextService,
    runtimeUiConfig: UiAppConfig,
    runtimeContext: UiRuntimeContext,
    filesModuleBackend: FilesModuleBackend,
    databaseModuleStore: DatabaseModuleRegistryOperations?,
    databaseModuleBackend: DatabaseModuleBackend?,
    runManager: RunManager,
    configFormService: ConfigFormService,
    sqlConsoleService: SqlConsoleOperations,
    sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations,
    sqlConsoleExportService: SqlConsoleExportService,
    sqlConsoleStateService: SqlConsoleStateService,
    uiConfigPersistenceService: UiConfigPersistenceService,
    moduleSyncService: ModuleSyncService?,
    databaseModuleRunService: DatabaseModuleRunOperations?,
    databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry,
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService?,
    databaseOutputRetentionService: DatabaseOutputRetentionService?,
    filesModuleRunHistoryService: ModuleRunHistoryService,
    databaseModuleRunHistoryService: ModuleRunHistoryService?,
): UiServerContext =
    UiServerContext(
        uiConfig = uiConfig,
        uiConfigLoader = uiConfigLoader,
        credentialsService = credentialsService,
        runtimeConfigResolver = runtimeConfigResolver,
        runtimeContextService = runtimeContextService,
        runtimeUiConfig = runtimeUiConfig,
        runtimeContext = runtimeContext,
        filesModuleBackend = filesModuleBackend,
        databaseModuleStore = databaseModuleStore,
        databaseModuleBackend = databaseModuleBackend,
        filesRunService = runManager,
        filesRunHistoryMaintenance = runManager,
        configFormService = configFormService,
        sqlConsoleService = sqlConsoleService,
        sqlConsoleQueryManager = sqlConsoleQueryManager,
        sqlConsoleExportService = sqlConsoleExportService,
        sqlConsoleStateService = sqlConsoleStateService,
        uiConfigPersistenceService = uiConfigPersistenceService,
        moduleSyncService = moduleSyncService,
        databaseModuleRunService = databaseModuleRunService,
        databaseModuleActiveRunRegistry = databaseModuleActiveRunRegistry,
        databaseRunHistoryCleanupService = databaseRunHistoryCleanupService,
        databaseOutputRetentionService = databaseOutputRetentionService,
        filesModuleRunHistoryService = filesModuleRunHistoryService,
        databaseModuleRunHistoryService = databaseModuleRunHistoryService,
    )

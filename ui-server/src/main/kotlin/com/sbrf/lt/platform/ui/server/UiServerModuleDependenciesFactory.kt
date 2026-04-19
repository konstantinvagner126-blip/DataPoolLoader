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
import com.sbrf.lt.platform.ui.module.ModuleRegistry
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

internal fun buildUiServerModuleContextDependencies(
    uiConfig: UiAppConfig,
    uiConfigLoader: UiConfigLoader? = null,
    credentialsService: UiCredentialsService? = null,
    runtimeConfigResolver: UiRuntimeConfigResolver? = null,
    runtimeContextService: UiRuntimeContextService? = null,
    runtimeUiConfig: UiAppConfig? = null,
    runtimeContext: UiRuntimeContext? = null,
    moduleRegistry: ModuleRegistry? = null,
    filesModuleBackend: FilesModuleBackend? = null,
    databaseModuleStore: DatabaseModuleRegistryOperations?,
    databaseModuleBackend: DatabaseModuleBackend?,
    runManager: RunManager? = null,
    configFormService: ConfigFormService? = null,
    sqlConsoleService: SqlConsoleOperations? = null,
    sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations? = null,
    sqlConsoleExportService: SqlConsoleExportService? = null,
    sqlConsoleStateService: SqlConsoleStateService? = null,
    uiConfigPersistenceService: UiConfigPersistenceService? = null,
    moduleSyncService: ModuleSyncService?,
    databaseModuleRunService: DatabaseModuleRunOperations?,
    databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry? = null,
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService?,
    databaseOutputRetentionService: DatabaseOutputRetentionService?,
    filesModuleRunHistoryService: ModuleRunHistoryService? = null,
    databaseModuleRunHistoryService: ModuleRunHistoryService?,
): UiServerContextDependencies {
    val resolvedUiConfigLoader = uiConfigLoader ?: defaultUiConfigLoader(uiConfig)
    val resolvedCredentialsService = credentialsService ?: defaultUiCredentialsService(uiConfig, resolvedUiConfigLoader)
    val resolvedRuntimeContextService = runtimeContextService ?: UiRuntimeContextService()
    val resolvedRuntimeConfigResolver = runtimeConfigResolver ?: UiRuntimeConfigResolver(resolvedCredentialsService)
    val resolvedRuntimeUiConfig = runtimeUiConfig ?: resolvedRuntimeConfigResolver.resolve(uiConfig)
    val resolvedRuntimeContext = runtimeContext ?: resolvedRuntimeContextService.resolve(resolvedRuntimeUiConfig)
    val resolvedModuleRegistry = moduleRegistry ?: defaultModuleRegistry(uiConfig)
    val resolvedFilesModuleBackend = filesModuleBackend ?: defaultFilesModuleBackend(resolvedModuleRegistry)
    val resolvedRunManager = runManager ?: defaultRunManager(resolvedModuleRegistry, uiConfig, resolvedCredentialsService)
    val resolvedConfigFormService = configFormService ?: ConfigFormService()
    val resolvedSqlConsoleService = sqlConsoleService ?: defaultSqlConsoleService(resolvedRuntimeUiConfig)
    val resolvedSqlConsoleQueryManager =
        sqlConsoleQueryManager ?: defaultSqlConsoleQueryManager(resolvedSqlConsoleService)
    val resolvedSqlConsoleExportService = sqlConsoleExportService ?: SqlConsoleExportService()
    val resolvedSqlConsoleStateService = sqlConsoleStateService ?: defaultSqlConsoleStateService(uiConfig)
    val resolvedUiConfigPersistenceService = uiConfigPersistenceService ?: UiConfigPersistenceService()
    val resolvedDatabaseModuleActiveRunRegistry =
        databaseModuleActiveRunRegistry ?: DatabaseModuleActiveRunRegistry()
    val resolvedFilesModuleRunHistoryService =
        filesModuleRunHistoryService ?: defaultFilesRunHistoryService(resolvedModuleRegistry, resolvedRunManager)

    return UiServerContextDependencies(
        uiConfig = uiConfig,
        uiConfigLoader = resolvedUiConfigLoader,
        credentialsService = resolvedCredentialsService,
        runtimeConfigResolver = resolvedRuntimeConfigResolver,
        runtimeContextService = resolvedRuntimeContextService,
        runtimeUiConfig = resolvedRuntimeUiConfig,
        runtimeContext = resolvedRuntimeContext,
        filesModuleBackend = resolvedFilesModuleBackend,
        databaseModuleStore = databaseModuleStore,
        databaseModuleBackend = databaseModuleBackend,
        runManager = resolvedRunManager,
        configFormService = resolvedConfigFormService,
        sqlConsoleService = resolvedSqlConsoleService,
        sqlConsoleQueryManager = resolvedSqlConsoleQueryManager,
        sqlConsoleExportService = resolvedSqlConsoleExportService,
        sqlConsoleStateService = resolvedSqlConsoleStateService,
        uiConfigPersistenceService = resolvedUiConfigPersistenceService,
        moduleSyncService = moduleSyncService,
        databaseModuleRunService = databaseModuleRunService,
        databaseModuleActiveRunRegistry = resolvedDatabaseModuleActiveRunRegistry,
        databaseRunHistoryCleanupService = databaseRunHistoryCleanupService,
        databaseOutputRetentionService = databaseOutputRetentionService,
        filesModuleRunHistoryService = resolvedFilesModuleRunHistoryService,
        databaseModuleRunHistoryService = databaseModuleRunHistoryService,
    )
}

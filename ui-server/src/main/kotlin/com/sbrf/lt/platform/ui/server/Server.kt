package com.sbrf.lt.platform.ui.server

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
import com.sbrf.lt.platform.ui.run.history.FilesModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import io.ktor.server.application.Application

fun Application.uiModule(
    uiConfig: UiAppConfig = UiConfigLoader().load(),
    uiConfigLoader: UiConfigLoader? = null,
    credentialsService: UiCredentialsService? = null,
    runtimeConfigResolver: UiRuntimeConfigResolver? = null,
    runtimeContextService: UiRuntimeContextService? = null,
    runtimeUiConfig: UiAppConfig? = null,
    runtimeContext: UiRuntimeContext? = null,
    moduleRegistry: ModuleRegistry? = null,
    filesModuleBackend: FilesModuleBackend? = null,
    databaseModuleStore: DatabaseModuleRegistryOperations? = null,
    databaseModuleBackend: DatabaseModuleBackend? = null,
    runManager: RunManager? = null,
    configFormService: ConfigFormService? = null,
    sqlConsoleService: SqlConsoleOperations? = null,
    sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations? = null,
    sqlConsoleExportService: SqlConsoleExportService? = null,
    sqlConsoleStateService: SqlConsoleStateService? = null,
    uiConfigPersistenceService: UiConfigPersistenceService? = null,
    moduleSyncService: ModuleSyncService? = null,
    databaseModuleRunService: DatabaseModuleRunOperations? = null,
    databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry? = null,
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService? = null,
    databaseOutputRetentionService: DatabaseOutputRetentionService? = null,
    filesModuleRunHistoryService: ModuleRunHistoryService? = null,
    databaseModuleRunHistoryService: ModuleRunHistoryService? = null,
) {
    val serverContext = buildUiServerContext(
        buildUiServerModuleContextDependencies(
            uiConfig = uiConfig,
            uiConfigLoader = uiConfigLoader,
            credentialsService = credentialsService,
            runtimeConfigResolver = runtimeConfigResolver,
            runtimeContextService = runtimeContextService,
            runtimeUiConfig = runtimeUiConfig,
            runtimeContext = runtimeContext,
            moduleRegistry = moduleRegistry,
            filesModuleBackend = filesModuleBackend,
            databaseModuleStore = databaseModuleStore,
            databaseModuleBackend = databaseModuleBackend,
            runManager = runManager,
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
        ),
    )
    installUiServerApplication(serverContext)
}

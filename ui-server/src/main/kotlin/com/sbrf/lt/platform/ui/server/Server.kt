package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.storageDirPath
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
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

fun Application.uiModule(
    uiConfig: UiAppConfig = UiConfigLoader().load(),
    uiConfigLoader: UiConfigLoader = object : UiConfigLoader() {
        override fun load(): UiAppConfig = uiConfig
    },
    credentialsService: UiCredentialsService = UiCredentialsService(
        uiConfigProvider = { runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig) },
    ),
    runtimeConfigResolver: UiRuntimeConfigResolver = UiRuntimeConfigResolver(credentialsService),
    runtimeContextService: UiRuntimeContextService = UiRuntimeContextService(),
    runtimeUiConfig: UiAppConfig = runtimeConfigResolver.resolve(uiConfig),
    runtimeContext: UiRuntimeContext = runtimeContextService.resolve(runtimeUiConfig),
    moduleRegistry: ModuleRegistry = defaultModuleRegistry(uiConfig),
    filesModuleBackend: FilesModuleBackend = defaultFilesModuleBackend(moduleRegistry),
    databaseModuleStore: DatabaseModuleRegistryOperations? = null,
    databaseModuleBackend: DatabaseModuleBackend? = null,
    runManager: RunManager = defaultRunManager(moduleRegistry, uiConfig, credentialsService),
    configFormService: ConfigFormService = ConfigFormService(),
    sqlConsoleService: SqlConsoleOperations = defaultSqlConsoleService(runtimeUiConfig),
    sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations = defaultSqlConsoleQueryManager(sqlConsoleService),
    sqlConsoleExportService: SqlConsoleExportService = SqlConsoleExportService(),
    sqlConsoleStateService: SqlConsoleStateService = defaultSqlConsoleStateService(uiConfig),
    uiConfigPersistenceService: UiConfigPersistenceService = UiConfigPersistenceService(),
    moduleSyncService: ModuleSyncService? = null,
    databaseModuleRunService: DatabaseModuleRunOperations? = null,
    databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry = DatabaseModuleActiveRunRegistry(),
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService? = null,
    databaseOutputRetentionService: DatabaseOutputRetentionService? = null,
    filesModuleRunHistoryService: ModuleRunHistoryService = defaultFilesRunHistoryService(moduleRegistry, runManager),
    databaseModuleRunHistoryService: ModuleRunHistoryService? = null,
) {
    val logger = defaultUiServerLogger()
    val mapper = createUiServerObjectMapper()
    installUiServerPlugins(logger)
    val serverContext = UiServerContext(
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

    routing {
        registerUiRoutes(serverContext, mapper)
    }
}

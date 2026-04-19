package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
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
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleActiveRunRegistry
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.history.DatabaseModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.FilesModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleAsyncQueryOperations
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.platform.ui.sync.DatabaseModuleSyncImporter
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun interface UiServerStarter {
    fun start(port: Int, module: Application.() -> Unit)
}

internal fun defaultUiServerStarter(): UiServerStarter = UiServerStarter { port, module ->
    embeddedServer(Netty, port = port, module = module).start(wait = true)
}

internal fun loadStaticText(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): String {
    return classLoader.getResourceAsStream(resourcePath)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Ресурс $resourcePath не найден")
}

internal fun uiStartupModule(
    uiConfig: UiAppConfig,
    logger: Logger,
    runtimeConfigResolver: UiRuntimeConfigResolver = UiRuntimeConfigResolver(),
    runtimeContext: UiRuntimeContext = UiRuntimeContextService().resolve(runtimeConfigResolver.resolve(uiConfig)),
    moduleInstaller: Application.() -> Unit = {
        uiModule(
            uiConfig = uiConfig,
            uiConfigLoader = UiConfigLoader(),
            runtimeContext = runtimeContext,
        )
    },
): Application.() -> Unit = {
    environment.monitor.subscribe(ApplicationStarted) {
        logger.info("UI успешно запущен. Переходи по ссылке для открытия страницы с интерфейсом: http://localhost:${uiConfig.port}")
    }
    moduleInstaller()
}

fun startUiServer(
    uiConfig: UiAppConfig = UiConfigLoader().load(),
    logger: Logger = LoggerFactory.getLogger("com.sbrf.lt.platform.ui.Startup"),
    starter: UiServerStarter = defaultUiServerStarter(),
    runtimeContextService: UiRuntimeContextService = UiRuntimeContextService(),
) {
    val port = uiConfig.port
    val appsRoot = uiConfig.appsRootPath()
    val storageDir = uiConfig.storageDirPath()
    val uiConfigLoader = UiConfigLoader()
    val credentialsService = UiCredentialsService(
        uiConfigProvider = { runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig) },
    )
    val runtimeConfigResolver = UiRuntimeConfigResolver(credentialsService)
    val runtimeContext = runtimeContextService.resolve(runtimeConfigResolver.resolve(uiConfig))
    if (appsRoot != null) {
        logger.info("Apps root определен: {}", appsRoot)
    } else {
        logger.warn("Apps root не определен. Список app-модулей будет пустым, пока не задан ui.appsRoot.")
    }
    logger.info("Каталог состояния UI: {}", storageDir)
    logger.info(
        "Runtime context UI: requestedMode={}, effectiveMode={}, dbConfigured={}, dbAvailable={}",
        runtimeContext.requestedMode,
        runtimeContext.effectiveMode,
        runtimeContext.database.configured,
        runtimeContext.database.available,
    )
    runtimeContext.fallbackReason?.let { reason ->
        logger.warn("Режим UI переведен в FILES: {}", reason)
    }
    starter.start(
        port,
        uiStartupModule(
            uiConfig = uiConfig,
            logger = logger,
            runtimeConfigResolver = runtimeConfigResolver,
            runtimeContext = runtimeContext,
            moduleInstaller = {
                uiModule(
                    uiConfig = uiConfig,
                    uiConfigLoader = uiConfigLoader,
                    credentialsService = credentialsService,
                    runtimeConfigResolver = runtimeConfigResolver,
                    runtimeContext = runtimeContext,
                )
            },
        ),
    )
}

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
    moduleRegistry: ModuleRegistry = ModuleRegistry(appsRoot = uiConfig.appsRootPath()),
    filesModuleBackend: FilesModuleBackend = FilesModuleBackend(moduleRegistry),
    databaseModuleStore: DatabaseModuleRegistryOperations? = null,
    databaseModuleBackend: DatabaseModuleBackend? = null,
    runManager: RunManager = RunManager(moduleRegistry = moduleRegistry, uiConfig = uiConfig, credentialsService = credentialsService),
    configFormService: ConfigFormService = ConfigFormService(),
    sqlConsoleService: SqlConsoleOperations = SqlConsoleService(runtimeUiConfig.sqlConsole),
    sqlConsoleQueryManager: SqlConsoleAsyncQueryOperations = SqlConsoleQueryManager(sqlConsoleService),
    sqlConsoleExportService: SqlConsoleExportService = SqlConsoleExportService(),
    sqlConsoleStateService: SqlConsoleStateService = SqlConsoleStateService(uiConfig.storageDirPath()),
    uiConfigPersistenceService: UiConfigPersistenceService = UiConfigPersistenceService(),
    moduleSyncService: ModuleSyncService? = null,
    databaseModuleRunService: DatabaseModuleRunOperations? = null,
    databaseModuleActiveRunRegistry: DatabaseModuleActiveRunRegistry = DatabaseModuleActiveRunRegistry(),
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService? = null,
    databaseOutputRetentionService: DatabaseOutputRetentionService? = null,
    filesModuleRunHistoryService: ModuleRunHistoryService = FilesModuleRunHistoryService(moduleRegistry, runManager),
    databaseModuleRunHistoryService: ModuleRunHistoryService? = null,
) {
    val logger = LoggerFactory.getLogger("UiServerHttp")
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

package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.isConfigured
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunStartRequest
import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.CreateDbModuleRequest
import com.sbrf.lt.platform.ui.model.SyncOneModuleRequest
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.module.backend.FilesModuleBackend
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
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
import com.sbrf.lt.datapool.module.sync.ModuleSyncState
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateStore
import com.sbrf.lt.platform.ui.sync.DatabaseModuleSyncImporter
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.flow.collect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

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
    sqlConsoleQueryManager: SqlConsoleQueryManager = SqlConsoleQueryManager(sqlConsoleService),
    sqlConsoleExportService: SqlConsoleExportService = SqlConsoleExportService(),
    sqlConsoleStateService: SqlConsoleStateService = SqlConsoleStateService(SqlConsoleStateStore(uiConfig.storageDirPath())),
    uiConfigPersistenceService: UiConfigPersistenceService = UiConfigPersistenceService(),
    moduleSyncService: ModuleSyncService? = null,
    databaseModuleRunService: DatabaseModuleRunOperations? = null,
    databaseRunHistoryCleanupService: DatabaseRunHistoryCleanupService? = null,
    databaseOutputRetentionService: DatabaseOutputRetentionService? = null,
    filesModuleRunHistoryService: ModuleRunHistoryService = FilesModuleRunHistoryService(moduleRegistry, runManager),
    databaseModuleRunHistoryService: ModuleRunHistoryService? = null,
) {
    val mapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    install(CallLogging) {
        level = Level.INFO
    }
    install(WebSockets)
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Неизвестная ошибка")))
        }
    }
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
        databaseRunHistoryCleanupService = databaseRunHistoryCleanupService,
        databaseOutputRetentionService = databaseOutputRetentionService,
        filesModuleRunHistoryService = filesModuleRunHistoryService,
        databaseModuleRunHistoryService = databaseModuleRunHistoryService,
    )

    routing {
        registerPageRoutes(serverContext)
        registerModuleRunRoutes(serverContext)
        registerDatabaseRoutes(serverContext, mapper)
        registerCommonRoutes(serverContext, mapper)
        registerSqlConsoleRoutes(serverContext)
    }
}

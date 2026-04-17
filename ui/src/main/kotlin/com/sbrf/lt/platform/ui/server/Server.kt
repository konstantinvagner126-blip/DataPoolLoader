package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.DatabaseModuleDetailsResponse
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
import com.sbrf.lt.platform.ui.model.toCatalogItemResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.sync.SyncRunResult
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExportService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleStateStore
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
    databaseModuleStore: DatabaseModuleStore? = null,
    runManager: RunManager = RunManager(moduleRegistry = moduleRegistry, uiConfig = uiConfig, credentialsService = credentialsService),
    configFormService: ConfigFormService = ConfigFormService(),
    sqlConsoleService: SqlConsoleService = SqlConsoleService(runtimeUiConfig.sqlConsole),
    sqlConsoleQueryManager: SqlConsoleQueryManager = SqlConsoleQueryManager(sqlConsoleService),
    sqlConsoleExportService: SqlConsoleExportService = SqlConsoleExportService(),
    sqlConsoleStateService: SqlConsoleStateService = SqlConsoleStateService(SqlConsoleStateStore(uiConfig.storageDirPath())),
    uiConfigPersistenceService: UiConfigPersistenceService = UiConfigPersistenceService(),
    moduleSyncService: ModuleSyncService? = null,
    databaseModuleRunService: DatabaseModuleRunService? = null,
) {
    val mapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    install(CallLogging) {
        level = Level.INFO
    }
    install(WebSockets)
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Неизвестная ошибка")))
        }
    }

    fun currentUiConfig(): UiAppConfig =
        runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig)

    fun currentRuntimeUiConfig(): UiAppConfig =
        runCatching { runtimeConfigResolver.resolve(currentUiConfig()) }.getOrDefault(runtimeUiConfig)

    fun currentRuntimeContext(): UiRuntimeContext =
        runCatching { runtimeContextService.resolve(currentRuntimeUiConfig()) }.getOrDefault(runtimeContext)

    fun currentDatabasePostgresConfig() =
        currentRuntimeUiConfig().moduleStore.postgres.takeIf { it.isConfigured() }

    fun currentDatabaseModuleStore(): DatabaseModuleStore? =
        databaseModuleStore ?: currentDatabasePostgresConfig()?.let { DatabaseModuleStore.fromConfig(it) }

    fun currentModuleSyncService(): ModuleSyncService? =
        moduleSyncService ?: currentDatabasePostgresConfig()?.let { ModuleSyncService.fromConfig(it) }

    fun currentDatabaseModuleRunService(): DatabaseModuleRunService? =
        databaseModuleRunService ?: currentDatabasePostgresConfig()?.let { postgres ->
            val store = currentDatabaseModuleStore() ?: DatabaseModuleStore.fromConfig(postgres)
            DatabaseModuleRunService(
                databaseModuleStore = store,
                executionSource = DatabaseModuleExecutionSource.fromConfig(postgres),
                runStore = DatabaseRunStore.fromConfig(postgres),
                credentialsProvider = credentialsService,
            )
        }

    fun readSyncStateSafely(): com.sbrf.lt.platform.ui.sync.ModuleSyncState =
        runCatching {
            val runtimeContext = currentRuntimeContext()
            val syncService = currentModuleSyncService()
            if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && syncService != null) {
                syncService.currentSyncState()
            } else {
                com.sbrf.lt.platform.ui.sync.ModuleSyncState()
            }
        }.getOrElse {
            com.sbrf.lt.platform.ui.sync.ModuleSyncState()
        }

    fun requireDatabaseMaintenanceIsInactive() {
        if (readSyncStateSafely().maintenanceMode) {
            error("Работа с DB-модулями временно недоступна: идет массовый импорт модулей в БД.")
        }
    }

    fun requireDatabaseModuleIsNotSyncing(moduleCode: String) {
        val activeSync = readSyncStateSafely().activeSingleSync(moduleCode)
        if (activeSync != null) {
            val startedBy = activeSync.startedByActorDisplayName ?: activeSync.startedByActorId
            val startedAt = activeSync.startedAt
            error(
                buildString {
                    append("Импорт модуля '$moduleCode' уже выполняется")
                    if (!startedBy.isNullOrBlank()) {
                        append(" пользователем $startedBy")
                    }
                    append(".")
                    append(" Начало: $startedAt.")
                },
            )
        }
    }

    routing {
        get("/") {
            val content = loadStaticText("static/home.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/modules") {
            if (currentRuntimeContext().effectiveMode != UiModuleStoreMode.FILES) {
                call.respondRedirect("/?modeAccessError=modules", permanent = false)
                return@get
            }
            val content = loadStaticText("static/index.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/help") {
            val content = loadStaticText("static/help.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/sql-console") {
            val content = loadStaticText("static/sql-console.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/db-modules") {
            if (currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
                call.respondRedirect("/?modeAccessError=db-modules", permanent = false)
                return@get
            }
            val content = loadStaticText("static/db-modules.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/db-sync") {
            if (currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
                call.respondRedirect("/?modeAccessError=db-sync", permanent = false)
                return@get
            }
            val content = loadStaticText("static/db-sync.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        staticResources("/static", "static")

        get("/api/modules") {
            call.respond(moduleRegistry.listModules().map { it.toCatalogItemResponse() })
        }

        get("/api/ui/runtime-context") {
            call.respond(currentRuntimeContext())
        }

        post("/api/ui/runtime-mode") {
            val request = call.receive<UiRuntimeModeUpdateRequest>()
            val updatedConfig = uiConfigPersistenceService.updateModuleStoreMode(request.mode)
            val updatedRuntimeContext = runtimeContextService.resolve(runtimeConfigResolver.resolve(updatedConfig))
            call.respond(
                UiRuntimeModeUpdateResponse(
                    message = "Предпочитаемый режим UI сохранен: ${request.mode.toConfigValue()}.",
                    runtimeContext = updatedRuntimeContext,
                ),
            )
        }

        get("/api/db/sync/state") {
            call.respond(readSyncStateSafely())
        }

        get("/api/db/modules/catalog") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            val store = currentDatabaseModuleStore()
            val modules = if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && store != null) {
                store.listModules()
            } else {
                emptyList()
            }
            call.respond(DatabaseModulesCatalogResponse(runtimeContext = runtimeContext, modules = modules))
        }

        get("/api/db/modules/{id}") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            val module = store.loadModuleDetails(
                moduleCode = requireNotNull(call.parameters["id"]),
                actorId = actorId,
                actorSource = actorSource,
            )
            call.respond(
                DatabaseModuleDetailsResponse(
                    runtimeContext = runtimeContext,
                    module = module.module,
                    sourceKind = module.sourceKind,
                    currentRevisionId = module.currentRevisionId,
                    workingCopyId = module.workingCopyId,
                    workingCopyStatus = module.workingCopyStatus,
                    baseRevisionId = module.baseRevisionId,
                ),
            )
        }

        get("/api/db/modules/{id}/runs") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val runService = requireNotNull(currentDatabaseModuleRunService()) {
                "DB run service не настроен."
            }
            call.respond(runService.listRuns(requireNotNull(call.parameters["id"])))
        }

        post("/api/db/modules/{id}/save") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val moduleCode = requireNotNull(call.parameters["id"])
            requireDatabaseModuleIsNotSyncing(moduleCode)
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            store.saveWorkingCopy(
                moduleCode = moduleCode,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = runtimeContext.actor.actorDisplayName,
                request = call.receive<SaveModuleRequest>(),
            )
            call.respond(SaveResultResponse("Изменения сохранены в личную working copy."))
        }

        post("/api/db/modules/{id}/discard-working-copy") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val moduleCode = requireNotNull(call.parameters["id"])
            requireDatabaseModuleIsNotSyncing(moduleCode)
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            store.discardWorkingCopy(
                moduleCode = moduleCode,
                actorId = actorId,
                actorSource = actorSource,
            )
            call.respond(SaveResultResponse("Личная working copy удалена."))
        }

        post("/api/db/modules/{id}/publish") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val moduleCode = requireNotNull(call.parameters["id"])
            requireDatabaseModuleIsNotSyncing(moduleCode)
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            val result = store.publishWorkingCopy(
                moduleCode = moduleCode,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = runtimeContext.actor.actorDisplayName,
            )
            call.respond(
                mapOf(
                    "message" to "Рабочая копия опубликована как revision #${result.revisionNo}",
                    "revisionId" to result.revisionId,
                    "revisionNo" to result.revisionNo,
                    "moduleCode" to result.moduleCode,
                )
            )
        }

        post("/api/db/modules/{id}/run") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val moduleCode = requireNotNull(call.parameters["id"])
            requireDatabaseModuleIsNotSyncing(moduleCode)
            val runService = requireNotNull(currentDatabaseModuleRunService()) {
                "DB run service не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            call.receive<DatabaseRunStartRequest>()
            call.respond(
                runService.startRun(
                    moduleCode = moduleCode,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = runtimeContext.actor.actorDisplayName,
                )
            )
        }

        post("/api/db/modules") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val actorSource = requireNotNull(runtimeContext.actor.actorSource) {
                "Для DB-режима нужно определить actorSource."
            }
            val request = call.receive<CreateDbModuleRequest>()
            val result = store.createModule(
                moduleCode = request.moduleCode,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = runtimeContext.actor.actorDisplayName,
                request = com.sbrf.lt.platform.ui.module.CreateModuleRequest(
                    title = request.title,
                    description = request.description,
                    tags = request.tags,
                    configText = request.configText,
                ),
            )
            call.respond(
                mapOf(
                    "message" to "DB-модуль '${result.moduleCode}' создан.",
                    "moduleId" to result.moduleId,
                    "moduleCode" to result.moduleCode,
                    "revisionId" to result.revisionId,
                    "workingCopyId" to result.workingCopyId,
                )
            )
        }

        delete("/api/db/modules/{id}") {
            requireDatabaseMaintenanceIsInactive()
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val moduleCode = requireNotNull(call.parameters["id"])
            requireDatabaseModuleIsNotSyncing(moduleCode)
            val store = requireNotNull(currentDatabaseModuleStore()) {
                "DB module store не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val result = store.deleteModule(
                moduleCode = moduleCode,
                actorId = actorId,
            )
            call.respond(
                mapOf(
                    "message" to "DB-модуль '${result.moduleCode}' удалён.",
                    "moduleCode" to result.moduleCode,
                    "deletedBy" to result.deletedBy,
                )
            )
        }

        post("/api/db/sync/one") {
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val syncService = requireNotNull(currentModuleSyncService()) {
                "ModuleSyncService не настроен."
            }
            val appsRoot = requireNotNull(currentRuntimeUiConfig().appsRootPath()) {
                "appsRoot не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val request = call.receive<SyncOneModuleRequest>()
            val result = syncService.syncOneFromFiles(
                moduleCode = request.moduleCode,
                appsRoot = appsRoot,
                actorId = actorId,
                actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
                actorDisplayName = runtimeContext.actor.actorDisplayName,
            )
            call.respond(result)
        }

        post("/api/db/sync/all") {
            val runtimeContext = currentRuntimeContext()
            require(runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE) {
                "DB-режим недоступен: ${runtimeContext.fallbackReason ?: "effective mode is ${runtimeContext.effectiveMode.toConfigValue()}"}"
            }
            val syncService = requireNotNull(currentModuleSyncService()) {
                "ModuleSyncService не настроен."
            }
            val appsRoot = requireNotNull(currentRuntimeUiConfig().appsRootPath()) {
                "appsRoot не настроен."
            }
            val actorId = requireNotNull(runtimeContext.actor.actorId) {
                "Для DB-режима нужно определить actorId."
            }
            val result = syncService.syncAllFromFiles(
                appsRoot = appsRoot,
                actorId = actorId,
                actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
                actorDisplayName = runtimeContext.actor.actorDisplayName,
            )
            call.respond(result)
        }

        get("/api/modules/catalog") {
            call.respond(
                ModulesCatalogResponse(
                    appsRootStatus = moduleRegistry.appsRootStatus(),
                    modules = moduleRegistry.listModules().map { it.toCatalogItemResponse() },
                ),
            )
        }

        get("/api/modules/{id}") {
            call.respond(runManager.loadModuleDetails(requireNotNull(call.parameters["id"])))
        }

        post("/api/modules/{id}/save") {
            moduleRegistry.saveModule(
                requireNotNull(call.parameters["id"]),
                call.receive<SaveModuleRequest>(),
            )
            call.respond(SaveResultResponse("Изменения модуля сохранены."))
        }

        post("/api/config-form/parse") {
            val request = call.receive<ConfigFormParseRequest>()
            call.respond(configFormService.parse(request.configText))
        }

        post("/api/config-form/update") {
            val request = call.receive<ConfigFormUpdateRequest>()
            call.respond(configFormService.apply(request.configText, request.formState))
        }

        post("/api/runs") {
            call.respond(runManager.startRun(call.receive<StartRunRequest>()))
        }

        get("/api/state") {
            call.respond(runManager.currentState())
        }

        get("/api/credentials") {
            call.respond(runManager.currentCredentialsStatus())
        }

        get("/api/sql-console/info") {
            call.respond(sqlConsoleService.info().toResponse())
        }

        get("/api/sql-console/state") {
            call.respond(sqlConsoleStateService.currentState())
        }

        post("/api/sql-console/state") {
            call.respond(sqlConsoleStateService.updateState(call.receive()))
        }

        post("/api/sql-console/settings") {
            val request = call.receive<SqlConsoleSettingsUpdateRequest>()
            uiConfigPersistenceService.updateSqlConsoleSettings(
                maxRowsPerShard = request.maxRowsPerShard,
                queryTimeoutSec = request.queryTimeoutSec,
            )
            call.respond(
                sqlConsoleService.updateSettings(
                    maxRowsPerShard = request.maxRowsPerShard,
                    queryTimeoutSec = request.queryTimeoutSec,
                ).toResponse()
            )
        }

        post("/api/sql-console/connections/check") {
            val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-check-")
            try {
                val credentialsPath = runManager.materializeCredentialsFile(tempDir)
                call.respond(
                    sqlConsoleService.checkConnections(credentialsPath = credentialsPath).toResponse(
                        configured = sqlConsoleService.info().configured,
                    ),
                )
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }

        post("/api/sql-console/query") {
            val request = call.receive<SqlConsoleQueryRequest>()
            val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-")
            try {
                val credentialsPath = runManager.materializeCredentialsFile(tempDir)
                call.respond(
                    sqlConsoleService.executeQuery(
                        rawSql = request.sql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = request.selectedSourceNames,
                    ).toResponse(),
                )
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }

        post("/api/sql-console/export/source-csv") {
            val request = call.receive<SqlConsoleExportRequest>()
            val shardName = requireNotNull(request.shardName?.takeIf { it.isNotBlank() }) {
                "Для CSV-экспорта нужно указать shardName."
            }
            val bytes = sqlConsoleExportService.exportShardCsv(request.result, shardName)
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "$shardName.csv",
                ).toString(),
            )
            call.respondBytes(bytes, ContentType.parse("text/csv; charset=utf-8"))
        }

        post("/api/sql-console/export/all-zip") {
            val request = call.receive<SqlConsoleExportRequest>()
            val bytes = sqlConsoleExportService.exportAllZip(request.result)
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "sql-console-results.zip",
                ).toString(),
            )
            call.respondBytes(bytes, ContentType.Application.Zip)
        }

        post("/api/sql-console/query/start") {
            val request = call.receive<SqlConsoleQueryRequest>()
            val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-")
            val credentialsPath = try {
                runManager.materializeCredentialsFile(tempDir)
            } catch (ex: Exception) {
                tempDir.toFile().deleteRecursively()
                throw ex
            }
            try {
                call.respond(
                    sqlConsoleQueryManager.startQuery(
                        sql = request.sql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = request.selectedSourceNames,
                        cleanupDir = tempDir,
                    ).toStartResponse(),
                )
            } catch (ex: Exception) {
                tempDir.toFile().deleteRecursively()
                throw ex
            }
        }

        get("/api/sql-console/query/{id}") {
            call.respond(sqlConsoleQueryManager.snapshot(requireNotNull(call.parameters["id"])).toResponse())
        }

        post("/api/sql-console/query/{id}/cancel") {
            call.respond(sqlConsoleQueryManager.cancel(requireNotNull(call.parameters["id"])).toResponse())
        }

        post("/api/credentials/upload") {
            val multipart = call.receiveMultipart()
            var fileName = "credential.properties"
            var content: String? = null
            while (true) {
                val part = multipart.readPart() ?: break
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: fileName
                        content = part.provider().readRemaining().readText()
                    }
                    else -> Unit
                }
                part.dispose.invoke()
            }
            require(!content.isNullOrBlank()) { "Не удалось прочитать содержимое credential.properties." }
            call.respond(runManager.uploadCredentials(fileName, requireNotNull(content)))
        }

        webSocket("/ws") {
            send(Frame.Text(mapper.writeValueAsString(runManager.currentState())))
            runManager.updates().collect { state ->
                send(Frame.Text(mapper.writeValueAsString(state)))
            }
        }
    }
}

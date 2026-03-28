package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.toCatalogItemResponse
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse
import com.sbrf.lt.platform.ui.module.ConfigFormService
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.RunManager
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
    moduleInstaller: Application.() -> Unit = { uiModule(uiConfig = uiConfig) },
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
) {
    val port = uiConfig.port
    val appsRoot = uiConfig.appsRootPath()
    val storageDir = uiConfig.storageDirPath()
    if (appsRoot != null) {
        logger.info("Apps root определен: {}", appsRoot)
    } else {
        logger.warn("Apps root не определен. Список app-модулей будет пустым, пока не задан ui.appsRoot.")
    }
    logger.info("Каталог состояния UI: {}", storageDir)
    starter.start(port, uiStartupModule(uiConfig, logger))
}

fun Application.uiModule(
    uiConfig: UiAppConfig = UiConfigLoader().load(),
    moduleRegistry: ModuleRegistry = ModuleRegistry(appsRoot = uiConfig.appsRootPath()),
    runManager: RunManager = RunManager(moduleRegistry = moduleRegistry, uiConfig = uiConfig),
    configFormService: ConfigFormService = ConfigFormService(),
    sqlConsoleService: SqlConsoleService = SqlConsoleService(uiConfig.sqlConsole),
    sqlConsoleQueryManager: SqlConsoleQueryManager = SqlConsoleQueryManager(sqlConsoleService),
    sqlConsoleExportService: SqlConsoleExportService = SqlConsoleExportService(),
    sqlConsoleStateService: SqlConsoleStateService = SqlConsoleStateService(SqlConsoleStateStore(uiConfig.storageDirPath())),
    uiConfigPersistenceService: UiConfigPersistenceService = UiConfigPersistenceService(),
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

    routing {
        get("/") {
            val content = loadStaticText("static/home.html")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/modules") {
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
        staticResources("/static", "static")

        get("/api/modules") {
            call.respond(moduleRegistry.listModules().map { it.toCatalogItemResponse() })
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

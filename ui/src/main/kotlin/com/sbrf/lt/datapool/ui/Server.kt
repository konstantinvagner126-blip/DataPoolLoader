package com.sbrf.lt.datapool.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.http.content.PartData
import io.ktor.websocket.Frame
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun startUiServer(port: Int = UiConfigLoader().load().port) {
    val logger = LoggerFactory.getLogger("com.sbrf.lt.datapool.ui.Startup")
    embeddedServer(Netty, port = port) {
        environment.monitor.subscribe(ApplicationStarted) {
            logger.info("UI успешно запущен. Переходи по ссылке для открытия страницы с интерфейсом: http://localhost:$port")
        }
        uiModule()
    }.start(wait = true)
}

fun Application.uiModule(
    moduleRegistry: ModuleRegistry = ModuleRegistry(),
    runManager: RunManager = RunManager(moduleRegistry = moduleRegistry),
    sqlConsoleService: SqlConsoleService = SqlConsoleService(UiConfigLoader().load().sqlConsole),
    sqlConsoleQueryManager: SqlConsoleQueryManager = SqlConsoleQueryManager(sqlConsoleService),
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
            val content = javaClass.classLoader.getResourceAsStream("static/index.html")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Ресурс static/index.html не найден")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        get("/sql-console") {
            val content = javaClass.classLoader.getResourceAsStream("static/sql-console.html")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Ресурс static/sql-console.html не найден")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        staticResources("/static", "static")

        get("/api/modules") {
            call.respond(moduleRegistry.listModules().map { mapOf("id" to it.id, "title" to it.title) })
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

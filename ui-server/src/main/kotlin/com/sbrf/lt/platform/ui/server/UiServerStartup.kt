package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.Logger

fun interface UiServerStarter {
    fun start(port: Int, module: Application.() -> Unit)
}

internal fun defaultUiServerStarter(): UiServerStarter = UiServerStarter { port, module ->
    embeddedServer(Netty, port = port, module = module).start(wait = true)
}

internal fun uiStartupModule(
    uiConfig: UiAppConfig,
    logger: Logger,
    runtimeContext: com.sbrf.lt.platform.ui.config.UiRuntimeContext,
    moduleInstaller: Application.() -> Unit = {
        uiModule(
            uiConfig = uiConfig,
            uiConfigLoader = UiConfigLoader(),
            runtimeContext = runtimeContext,
        )
    },
): Application.() -> Unit = {
    monitor.subscribe(ApplicationStarted) {
        logger.info("UI успешно запущен. Переходи по ссылке для открытия страницы с интерфейсом: http://localhost:${uiConfig.port}")
    }
    moduleInstaller()
}

fun startUiServer(
    uiConfig: UiAppConfig = UiConfigLoader().load(),
    logger: Logger = defaultUiServerStartupLogger(),
    starter: UiServerStarter = defaultUiServerStarter(),
    runtimeContextService: UiRuntimeContextService = UiRuntimeContextService(),
) {
    val startupRuntime = buildUiServerStartupRuntime(uiConfig, runtimeContextService)
    logUiStartupRuntime(logger, uiConfig, startupRuntime.runtimeContext)
    starter.start(
        uiConfig.port,
        uiStartupModule(
            uiConfig = uiConfig,
            logger = logger,
            runtimeContext = startupRuntime.runtimeContext,
            moduleInstaller = {
                uiModule(
                    uiConfig = uiConfig,
                    uiConfigLoader = startupRuntime.uiConfigLoader,
                    credentialsService = startupRuntime.credentialsService,
                    runtimeConfigResolver = startupRuntime.runtimeConfigResolver,
                    runtimeContext = startupRuntime.runtimeContext,
                )
            },
        ),
    )
}

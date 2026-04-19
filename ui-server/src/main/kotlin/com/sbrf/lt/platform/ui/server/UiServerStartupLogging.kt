package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.config.storageDirPath
import org.slf4j.Logger

internal fun logUiStartupRuntime(
    logger: Logger,
    uiConfig: UiAppConfig,
    runtimeContext: UiRuntimeContext,
) {
    val appsRoot = uiConfig.appsRootPath()
    val storageDir = uiConfig.storageDirPath()
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
}

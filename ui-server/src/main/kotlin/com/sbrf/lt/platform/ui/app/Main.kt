package com.sbrf.lt.platform.ui.app

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.server.startUiServer

internal var configLoaderFactory: () -> UiConfigLoader = ::UiConfigLoader
internal var uiStarter: (UiAppConfig) -> Unit = ::startUiServer
internal var mainEntryPoint: () -> Unit = ::runUi

fun main() {
    mainEntryPoint()
}

internal fun runUi(
    configLoader: UiConfigLoader = configLoaderFactory(),
    starter: (UiAppConfig) -> Unit = uiStarter,
) {
    starter(configLoader.load())
}

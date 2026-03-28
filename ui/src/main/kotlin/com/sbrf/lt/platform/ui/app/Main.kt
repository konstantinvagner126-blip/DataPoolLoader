package com.sbrf.lt.platform.ui.app

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.server.startUiServer

fun main() {
    runUi()
}

internal fun runUi(
    configLoader: UiConfigLoader = UiConfigLoader(),
    starter: (UiAppConfig) -> Unit = ::startUiServer,
) {
    starter(configLoader.load())
}

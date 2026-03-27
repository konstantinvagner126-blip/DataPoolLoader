package com.sbrf.lt.datapool.ui

fun main() {
    runUi()
}

internal fun runUi(
    configLoader: UiConfigLoader = UiConfigLoader(),
    starter: (Int) -> Unit = ::startUiServer,
) {
    starter(configLoader.load().port)
}

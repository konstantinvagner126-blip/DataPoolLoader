package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiConfigLoader

internal fun loadStaticText(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): String {
    return classLoader.getResourceAsStream(resourcePath)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Ресурс $resourcePath не найден")
}

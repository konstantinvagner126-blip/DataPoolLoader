package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ConfigLoader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal class UiConfigLoadSupport(
    private val configLoader: ConfigLoader,
) {
    fun load(
        externalConfig: Path?,
        classpathStream: InputStream?,
    ): UiAppConfig {
        if (externalConfig != null) {
            require(Files.exists(externalConfig)) {
                "UI-конфиг не найден: $externalConfig"
            }
            return readUiConfigFileWithBaseDir(externalConfig, configLoader)
        }

        val stream = classpathStream ?: return UiAppConfig()
        return stream.bufferedReader().use {
            configLoader.objectMapper().readValue(it, UiRootConfig::class.java).ui
        }
    }
}

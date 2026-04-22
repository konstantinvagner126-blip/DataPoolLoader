package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path

internal fun readUiConfigFile(
    path: Path,
    configLoader: ConfigLoader,
): UiAppConfig {
    return Files.newBufferedReader(path).use {
        configLoader.objectMapper().readValue(it, UiRootConfig::class.java).ui
    }
}

internal fun readUiConfigFileWithBaseDir(
    path: Path,
    configLoader: ConfigLoader,
): UiAppConfig {
    return readUiConfigFile(path, configLoader).copy(
        configBaseDir = path.parent?.toAbsolutePath()?.normalize()?.toString(),
    )
}

internal fun writeUiConfigFile(
    targetPath: Path,
    updatedUiConfig: UiAppConfig,
    configLoader: ConfigLoader,
) {
    targetPath.parent?.let { Files.createDirectories(it) }
    Files.newBufferedWriter(targetPath).use { writer ->
        configLoader.objectMapper().writeValue(writer, UiRootConfig(updatedUiConfig))
    }
}

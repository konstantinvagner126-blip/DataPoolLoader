package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.config.ConfigLoader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

data class UiRootConfig(
    val ui: UiAppConfig = UiAppConfig(),
)

data class UiAppConfig(
    val port: Int = 8080,
    val defaultCredentialsFile: String? = null,
)

class UiConfigLoader(
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    fun load(): UiAppConfig {
        val stream: InputStream = javaClass.classLoader.getResourceAsStream("application.yml")
            ?: return UiAppConfig()
        val root = stream.bufferedReader().use {
            configLoader.objectMapper().readValue(it, UiRootConfig::class.java)
        }
        return root.ui
    }
}

fun UiAppConfig.defaultCredentialsPath(): Path? {
    val configured = defaultCredentialsFile?.trim()?.takeIf { it.isNotEmpty() }?.let(Path::of)
    if (configured != null) {
        return configured
    }
    val system = System.getProperty("credentials.file")?.trim()?.takeIf { it.isNotEmpty() }?.let(Path::of)
    if (system != null) {
        return system
    }
    val local = Path.of("gradle", "credential.properties")
    return local.takeIf { Files.exists(it) }
}

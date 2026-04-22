package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.defaultCredentialsPath
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class UiCredentialsFallbackSupport(
    private val uiConfigProvider: () -> UiAppConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun currentCredentialsStatus(uploadedCredentials: UploadedCredentials?): CredentialsStatusResponse {
        if (uploadedCredentials != null) {
            return CredentialsStatusResponse(
                mode = "UPLOADED",
                displayName = uploadedCredentials.fileName,
                fileAvailable = true,
                uploaded = true,
            )
        }

        val fallback = currentFallbackPath()
        return if (fallback != null) {
            CredentialsStatusResponse(
                mode = "FILE",
                displayName = fallback.toString(),
                fileAvailable = Files.exists(fallback),
                uploaded = false,
            )
        } else {
            CredentialsStatusResponse(
                mode = "NONE",
                displayName = "Файл не задан",
                fileAvailable = false,
                uploaded = false,
            )
        }
    }

    fun materializeCredentialsFile(uploadedCredentials: UploadedCredentials?, tempDir: Path): Path? {
        if (uploadedCredentials != null) {
            val path = tempDir.resolve("credential.properties")
            path.writeText(uploadedCredentials.content)
            logger.info("Для запуска используется credential.properties, загруженный через UI: {}", uploadedCredentials.fileName)
            return path
        }
        val fallback = currentFallbackPath()
        val resolved = fallback?.takeIf { Files.exists(it) }
        if (resolved != null) {
            logger.info("Для запуска используется fallback credential.properties: {}", resolved)
        } else {
            logger.info("credential.properties для запуска не найден, будет использован только inline/env/system resolution")
        }
        return resolved
    }

    fun currentProperties(uploadedCredentials: UploadedCredentials?): Map<String, String> {
        if (uploadedCredentials != null) {
            return loadProperties(uploadedCredentials.content)
        }
        val fallback = currentFallbackPath()
        if (fallback != null && Files.exists(fallback)) {
            return loadProperties(fallback.readText())
        }
        return emptyMap()
    }

    private fun currentFallbackPath(): Path? = currentUiConfig().defaultCredentialsPath()

    private fun currentUiConfig(): UiAppConfig =
        runCatching(uiConfigProvider).getOrElse { UiAppConfig() }

    private fun loadProperties(content: String): Map<String, String> {
        val normalizedContent = content.removePrefix("\uFEFF")
        val props = Properties()
        props.load(StringReader(normalizedContent))
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}

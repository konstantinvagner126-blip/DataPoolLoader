package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.defaultCredentialsPath
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Общий источник `credential.properties` для FILES, DB runtime и SQL-консоли.
 */
class UiCredentialsService(
    private val uiConfigProvider: () -> UiAppConfig,
    private val stateStore: UiCredentialsStateStore = UiCredentialsStateStore(uiConfigProvider().storageDirPath()),
) : UiCredentialsProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var uploadedCredentials: UploadedCredentials? = stateStore.load().uploadedCredentials?.let {
        UploadedCredentials(fileName = it.fileName, content = it.content)
    }

    fun uploadCredentials(fileName: String, content: String): CredentialsStatusResponse {
        require(content.isNotBlank()) { "Файл credential.properties пуст." }
        uploadedCredentials = UploadedCredentials(fileName = fileName, content = content)
        persist()
        return currentCredentialsStatus()
    }

    override fun currentCredentialsStatus(): CredentialsStatusResponse {
        val uploaded = uploadedCredentials
        if (uploaded != null) {
            return CredentialsStatusResponse(
                mode = "UPLOADED",
                displayName = uploaded.fileName,
                fileAvailable = true,
                uploaded = true,
            )
        }

        val fallback = currentUiConfig().defaultCredentialsPath()
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

    override fun materializeCredentialsFile(tempDir: Path): Path? {
        val uploaded = uploadedCredentials
        if (uploaded != null) {
            val path = tempDir.resolve("credential.properties")
            path.writeText(uploaded.content)
            logger.info("Для запуска используется credential.properties, загруженный через UI: {}", uploaded.fileName)
            return path
        }
        val fallback = currentUiConfig().defaultCredentialsPath()
        val resolved = fallback?.takeIf { Files.exists(it) }
        if (resolved != null) {
            logger.info("Для запуска используется fallback credential.properties: {}", resolved)
        } else {
            logger.info("credential.properties для запуска не найден, будет использован только inline/env/system resolution")
        }
        return resolved
    }

    override fun currentProperties(): Map<String, String> {
        val uploaded = uploadedCredentials
        if (uploaded != null) {
            return loadProperties(uploaded.content)
        }
        val fallback = currentUiConfig().defaultCredentialsPath()
        if (fallback != null && Files.exists(fallback)) {
            return loadProperties(fallback.readText())
        }
        return emptyMap()
    }

    private fun currentUiConfig(): UiAppConfig =
        runCatching(uiConfigProvider).getOrElse { UiAppConfig() }

    private fun persist() {
        stateStore.save(
            PersistedCredentialsState(
                uploadedCredentials = uploadedCredentials?.let {
                    PersistedUploadedCredentials(
                        fileName = it.fileName,
                        content = it.content,
                    )
                },
            ),
        )
    }

    private fun loadProperties(content: String): Map<String, String> {
        val normalizedContent = content.removePrefix("\uFEFF")
        val props = Properties()
        props.load(StringReader(normalizedContent))
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }
}

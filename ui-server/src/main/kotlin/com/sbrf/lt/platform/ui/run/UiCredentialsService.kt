package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import java.nio.file.Path

/**
 * Общий источник `credential.properties` для FILES, DB runtime и SQL-консоли.
 */
class UiCredentialsService(
    private val uiConfigProvider: () -> UiAppConfig,
    private val stateStore: UiCredentialsStateStore = UiCredentialsStateStore(uiConfigProvider().storageDirPath()),
) : UiCredentialsProvider {
    private val persistenceSupport = UiCredentialsPersistenceSupport(stateStore)
    private val fallbackSupport = UiCredentialsFallbackSupport(uiConfigProvider)

    @Volatile
    private var uploadedCredentials: UploadedCredentials? = persistenceSupport.restoreUploadedCredentials()

    fun uploadCredentials(fileName: String, content: String): CredentialsStatusResponse {
        require(content.isNotBlank()) { "Файл credential.properties пуст." }
        uploadedCredentials = UploadedCredentials(fileName = fileName, content = content)
        persist()
        return currentCredentialsStatus()
    }

    override fun currentCredentialsStatus(): CredentialsStatusResponse =
        fallbackSupport.currentCredentialsStatus(uploadedCredentials)

    override fun materializeCredentialsFile(tempDir: Path): Path? =
        fallbackSupport.materializeCredentialsFile(uploadedCredentials, tempDir)

    override fun currentProperties(): Map<String, String> =
        fallbackSupport.currentProperties(uploadedCredentials)

    private fun persist() {
        persistenceSupport.persist(uploadedCredentials)
    }
}

package com.sbrf.lt.platform.ui.run

internal class UiCredentialsPersistenceSupport(
    private val stateStore: UiCredentialsStateStore,
) {
    fun restoreUploadedCredentials(): UploadedCredentials? = stateStore.load().uploadedCredentials?.let {
        UploadedCredentials(
            fileName = it.fileName,
            content = it.content,
        )
    }

    fun persist(uploadedCredentials: UploadedCredentials?) {
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
}

package com.sbrf.lt.platform.ui.run

/**
 * Состояние загруженного через UI `credential.properties`, сохраняемое отдельно от run-history.
 */
data class PersistedCredentialsState(
    val uploadedCredentials: PersistedUploadedCredentials? = null,
)

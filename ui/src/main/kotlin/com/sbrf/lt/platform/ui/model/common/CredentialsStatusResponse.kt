package com.sbrf.lt.platform.ui.model

/**
 * Состояние источника `credential.properties`, видимое из UI.
 */
data class CredentialsStatusResponse(
    val mode: String,
    val displayName: String,
    val fileAvailable: Boolean,
    val uploaded: Boolean,
)

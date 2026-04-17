package com.sbrf.lt.platform.ui.run

/**
 * Загруженный пользователем `credential.properties`, который UI временно держит в памяти.
 */
internal data class UploadedCredentials(
    val fileName: String,
    val content: String,
)

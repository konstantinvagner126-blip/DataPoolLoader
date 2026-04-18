package com.sbrf.lt.platform.ui.run

/**
 * Сериализуемое содержимое загруженного пользователем файла `credential.properties`.
 */
data class PersistedUploadedCredentials(
    val fileName: String,
    val content: String,
)

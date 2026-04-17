package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse

/**
 * Формирует понятное сообщение об отсутствующих значениях для credentials placeholders.
 */
fun buildMissingCredentialValuesMessage(
    subjectLabel: String,
    missingKeys: List<String>,
    credentialsStatus: CredentialsStatusResponse,
): String {
    val keys = missingKeys.joinToString(", ")
    return if (credentialsStatus.fileAvailable) {
        "Для выбранного $subjectLabel не удалось разрешить placeholders: $keys. " +
            "Проверены credential.properties, переменные окружения и JVM system properties."
    } else {
        "Для выбранного $subjectLabel не удалось разрешить placeholders: $keys. " +
            "credential.properties не найден. Сначала ищется ui.defaultCredentialsFile, затем gradle/credential.properties в проекте, затем ~/.gradle/credential.properties. " +
            "Можно загрузить файл через UI или задать значения через переменные окружения / JVM system properties."
    }
}

package com.sbrf.lt.platform.ui.run

/**
 * Состояние разрешения credentials placeholders для конкретного конфига модуля.
 */
data class CredentialRequirement(
    val requiredKeys: List<String>,
    val missingKeys: List<String>,
) {
    val requiresCredentials: Boolean
        get() = requiredKeys.isNotEmpty()

    val ready: Boolean
        get() = missingKeys.isEmpty()
}

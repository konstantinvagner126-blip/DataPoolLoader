package com.sbrf.lt.platform.ui.run

/**
 * Проверяет, использует ли конфиг placeholders вида `${...}` для credentials/runtime resolution.
 */
fun containsCredentialPlaceholders(configText: String): Boolean =
    extractCredentialPlaceholderKeys(configText).isNotEmpty()

/**
 * Возвращает уникальные placeholder keys в порядке появления в конфиге.
 */
fun extractCredentialPlaceholderKeys(configText: String): List<String> {
    val keys = linkedSetOf<String>()
    PLACEHOLDER_PATTERN.findAll(configText).forEach { match ->
        keys += match.groupValues[1]
    }
    return keys.toList()
}

/**
 * Проверяет, какие placeholders реально могут быть разрешены на текущем runtime.
 */
fun analyzeCredentialRequirements(
    configText: String,
    credentialProperties: Map<String, String>,
    environmentLookup: (String) -> String? = System::getenv,
    systemPropertyLookup: (String) -> String? = System::getProperty,
): CredentialRequirement {
    val requiredKeys = extractCredentialPlaceholderKeys(configText)
    val missingKeys = requiredKeys.filter { key ->
        credentialProperties[key] == null &&
            environmentLookup(key) == null &&
            systemPropertyLookup(key) == null
    }
    return CredentialRequirement(
        requiredKeys = requiredKeys,
        missingKeys = missingKeys,
    )
}

private val PLACEHOLDER_PATTERN = Regex("""\$\{([A-Za-z0-9_.-]+)}""")

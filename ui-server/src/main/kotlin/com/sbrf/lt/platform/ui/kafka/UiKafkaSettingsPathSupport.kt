package com.sbrf.lt.platform.ui.kafka

import java.nio.file.Path

internal fun resolveKafkaSettingsPath(
    rawValue: String,
    configBaseDir: Path?,
): Path? {
    val trimmed = rawValue.trim()
    val filePlaceholderPrefix = "\${file:"
    val rawPath = when {
        trimmed.startsWith(filePlaceholderPrefix) && trimmed.endsWith("}") ->
            trimmed.removePrefix(filePlaceholderPrefix).removeSuffix("}")
        trimmed.startsWith("\${") -> return null
        else -> trimmed
    }
    if (rawPath.isBlank()) {
        return null
    }
    val path = Path.of(rawPath)
    return if (path.isAbsolute) {
        path.normalize()
    } else {
        configBaseDir?.resolve(path)?.normalize() ?: path.normalize()
    }
}

internal fun formatKafkaSettingsPathValue(
    targetProperty: String,
    selectedPath: Path,
): String {
    val normalized = selectedPath.toAbsolutePath().normalize().toString()
    return when (targetProperty) {
        "ssl.truststore.certificates",
        "ssl.keystore.certificate.chain",
        "ssl.keystore.key",
            -> "\${file:$normalized}"

        else -> normalized
    }
}

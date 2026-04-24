package com.sbrf.lt.platform.ui.config

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class UiConfigPlaceholderResolutionSupport {

    fun resolveIfPossible(
        rawValue: String,
        properties: Map<String, String>,
        baseDir: Path? = null,
    ): String {
        resolveFilePlaceholder(rawValue, baseDir)?.let { return it }

        val trimmed = rawValue.trim()
        val match = placeholderPattern.matchEntire(trimmed) ?: return rawValue
        val key = match.groupValues[1]
        return properties[key]
            ?: System.getenv(key)
            ?: System.getProperty(key)
            ?: rawValue
    }

    private fun resolveFilePlaceholder(
        rawValue: String,
        baseDir: Path?,
    ): String? {
        val trimmed = rawValue.trim()
        val match = filePlaceholderPattern.matchEntire(trimmed) ?: return null
        val configuredPath = match.groupValues[1].trim().takeIf { it.isNotEmpty() } ?: return rawValue
        val rawPath = runCatching { Path.of(configuredPath) }.getOrNull() ?: return rawValue
        val resolvedPath = when {
            rawPath.isAbsolute -> rawPath.normalize()
            baseDir != null -> baseDir.resolve(rawPath).normalize()
            else -> rawPath.toAbsolutePath().normalize()
        }
        if (!resolvedPath.exists()) {
            return rawValue
        }
        return resolvedPath.readText().removePrefix("\uFEFF")
    }

    private companion object {
        val placeholderPattern = Regex("^\\$\\{([A-Za-z0-9_.-]+)}$")
        val filePlaceholderPattern = Regex("^\\$\\{file:(.+)}$")
    }
}

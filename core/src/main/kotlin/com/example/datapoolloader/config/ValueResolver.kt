package com.example.datapoolloader.config

import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class ValueResolver private constructor(
    private val properties: Map<String, String>,
) {
    private val placeholderPattern = Regex("^\\$\\{([A-Za-z0-9_.-]+)}$")

    fun resolve(rawValue: String): String {
        val trimmed = rawValue.trim()
        val match = placeholderPattern.matchEntire(trimmed) ?: return rawValue
        val key = match.groupValues[1]

        return properties[key]
            ?: System.getenv(key)
            ?: System.getProperty(key)
            ?: throw IllegalArgumentException("Value for placeholder $trimmed is not defined in credentials file, environment variables, or JVM system properties.")
    }

    companion object {
        fun fromFile(path: Path?): ValueResolver {
            if (path == null || Files.notExists(path)) {
                return ValueResolver(emptyMap())
            }

            Files.newBufferedReader(path).use { reader ->
                return ValueResolver(loadProperties(reader))
            }
        }

        private fun loadProperties(reader: Reader): Map<String, String> {
            val props = Properties()
            props.load(reader)
            return props.stringPropertyNames().associateWith { props.getProperty(it) }
        }
    }
}

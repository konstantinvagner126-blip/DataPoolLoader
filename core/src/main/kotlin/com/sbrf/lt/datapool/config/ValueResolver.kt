package com.sbrf.lt.datapool.config

import java.io.Reader
import java.io.StringReader
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
            ?: throw IllegalArgumentException("Значение для placeholder $trimmed не найдено в credentials-файле, переменных окружения или JVM system properties.")
    }

    companion object {
        fun fromFile(path: Path?): ValueResolver {
            if (path == null) {
                return ValueResolver(emptyMap())
            }

            require(Files.exists(path)) { "Файл credentials не найден: $path" }

            Files.newBufferedReader(path).use { reader ->
                return ValueResolver(loadProperties(reader))
            }
        }

        private fun loadProperties(reader: Reader): Map<String, String> {
            val normalizedContent = reader.readText().removePrefix("\uFEFF")
            val props = Properties()
            props.load(StringReader(normalizedContent))
            return props.stringPropertyNames().associateWith { props.getProperty(it) }
        }
    }
}

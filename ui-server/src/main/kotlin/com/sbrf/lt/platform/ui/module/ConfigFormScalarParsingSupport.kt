package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

internal class ConfigFormScalarParsingSupport {
    fun readText(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: String,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): String {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return if (node.isValueNode) {
            node.asText()
        } else {
            warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
            defaultValue
        }
    }

    fun readFirstText(
        parent: ObjectNode?,
        fieldNames: List<String>,
        defaultValue: String,
        warnings: MutableList<String>,
    ): String {
        fieldNames.forEach { fieldName ->
            val path = "app.$fieldName"
            val node = parent?.path(fieldName)
            if (node != null && !node.isMissingNode && !node.isNull) {
                return readText(parent, fieldName, defaultValue, warnings, path)
            }
        }
        return defaultValue
    }

    fun readOptionalText(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: String?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): String? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return if (node.isValueNode) {
            node.asText().takeUnless { it.isBlank() }
        } else {
            warnings += "Поле $path имеет неподдерживаемый тип. Значение пропущено."
            defaultValue
        }
    }

    fun readInt(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Int,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Int {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseIntNode(node, path, warnings) ?: defaultValue
    }

    fun readOptionalInt(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Int?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Int? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseIntNode(node, path, warnings) ?: defaultValue
    }

    fun readLong(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Long,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Long {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseLongNode(node, path, warnings) ?: defaultValue
    }

    fun readOptionalLong(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Long?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Long? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseLongNode(node, path, warnings) ?: defaultValue
    }

    fun readOptionalDouble(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Double?,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Double? {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseDoubleNode(node, path, warnings) ?: defaultValue
    }

    fun readBoolean(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: Boolean,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): Boolean {
        val node = parent?.path(fieldName)
        if (node == null || node.isMissingNode || node.isNull) {
            return defaultValue
        }
        return parseBooleanNode(node, path, warnings) ?: defaultValue
    }

    fun <T : Enum<T>> readEnum(
        parent: ObjectNode?,
        fieldName: String,
        defaultValue: T,
        entries: List<T>,
        warnings: MutableList<String>,
        path: String = "app.$fieldName",
    ): T {
        val rawValue = readOptionalText(parent, fieldName, null, warnings, path) ?: return defaultValue
        return entries.firstOrNull { it.name.equals(rawValue.trim(), ignoreCase = true) }
            ?: defaultValue.also {
                warnings += "Поле $path содержит неизвестное значение '$rawValue'. Использовано значение по умолчанию."
            }
    }

    private fun parseIntNode(node: JsonNode, path: String, warnings: MutableList<String>): Int? {
        if (node.isInt || node.isLong) {
            return node.asInt()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toIntOrNull()
                ?: run {
                    warnings += "Поле $path должно быть числом. Использовано значение по умолчанию."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }

    private fun parseLongNode(node: JsonNode, path: String, warnings: MutableList<String>): Long? {
        if (node.isIntegralNumber) {
            return node.asLong()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toLongOrNull()
                ?: run {
                    warnings += "Поле $path должно быть целым числом. Использовано значение по умолчанию."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }

    private fun parseDoubleNode(node: JsonNode, path: String, warnings: MutableList<String>): Double? {
        if (node.isNumber) {
            return node.asDouble()
        }
        if (node.isTextual) {
            return node.asText().trim().takeUnless { it.isBlank() }?.toDoubleOrNull()
                ?: run {
                    warnings += "Поле $path должно быть числом. Значение пропущено."
                    null
                }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Значение пропущено."
        return null
    }

    private fun parseBooleanNode(node: JsonNode, path: String, warnings: MutableList<String>): Boolean? {
        if (node.isBoolean) {
            return node.booleanValue()
        }
        if (node.isTextual) {
            return when (node.asText().trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> {
                    warnings += "Поле $path должно быть true/false. Использовано значение по умолчанию."
                    null
                }
            }
        }
        warnings += "Поле $path имеет неподдерживаемый тип. Использовано значение по умолчанию."
        return null
    }
}

package com.sbrf.lt.datapool.module.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.sql.SqlFileReference
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor

/**
 * Общая проверка конфигурации модуля для FILES и DATABASE storage.
 */
class ModuleValidationService(
    private val objectMapper: ObjectMapper = ConfigLoader().objectMapper(),
) {
    fun validate(
        configText: String,
        sqlReferenceExists: (SqlFileReference) -> Boolean = { true },
        additionalIssues: List<ModuleValidationIssue> = emptyList(),
    ): ModuleValidationResult {
        val issues = additionalIssues.toMutableList()
        val normalizedConfigText = configText.trim()

        if (normalizedConfigText.isBlank()) {
            issues += ModuleValidationIssue(
                severity = ModuleValidationSeverity.ERROR,
                message = "application.yml пустой или не задан.",
            )
            return issues.toValidationResult()
        }

        val root = try {
            objectMapper.readTree(configText)
        } catch (error: Exception) {
            issues += ModuleValidationIssue(
                severity = ModuleValidationSeverity.ERROR,
                message = "application.yml не удалось разобрать: ${error.message ?: "ошибка синтаксиса YAML"}.",
            )
            return issues.toValidationResult()
        }

        SqlFileReferenceExtractor.extractOrEmpty(configText, objectMapper).forEach { reference ->
            if (!sqlReferenceExists(reference)) {
                issues += ModuleValidationIssue(
                    severity = ModuleValidationSeverity.ERROR,
                    message = "Не найден SQL-файл ${reference.path} (${reference.label}).",
                )
            }
        }

        val duplicateSourceNames = root.path("app").path("sources")
            .takeIf { it.isArray }
            ?.mapNotNull { source ->
                source.path("name").takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
            }
            ?.groupingBy { it }
            ?.eachCount()
            ?.filterValues { it > 1 }
            ?.keys
            ?.sorted()
            .orEmpty()

        if (duplicateSourceNames.isNotEmpty()) {
            issues += ModuleValidationIssue(
                severity = ModuleValidationSeverity.WARNING,
                message = "Повторяются имена sources: ${duplicateSourceNames.joinToString(", ")}.",
            )
        }

        return issues.toValidationResult()
    }

    private fun List<ModuleValidationIssue>.toValidationResult(): ModuleValidationResult =
        ModuleValidationResult(
            status = when {
                any { it.severity == ModuleValidationSeverity.ERROR } -> ModuleValidationStatus.INVALID
                any { it.severity == ModuleValidationSeverity.WARNING } -> ModuleValidationStatus.WARNING
                else -> ModuleValidationStatus.VALID
            },
            issues = this,
        )
}

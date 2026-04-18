package com.sbrf.lt.datapool.module.validation

import com.fasterxml.jackson.databind.JsonNode
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

        issues += validateAppBusinessRules(root)

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

    private fun validateAppBusinessRules(root: JsonNode): List<ModuleValidationIssue> {
        val issues = mutableListOf<ModuleValidationIssue>()
        val app = root.path("app")
        val sources = app.path("sources")
        val commonSqlConfigured = app.text("commonSql").isNotBlank() || app.text("commonSqlFile").isNotBlank()

        if (!sources.isArray || sources.isEmpty) {
            issues += error("Должен быть настроен хотя бы один источник.")
        } else {
            sources.forEachIndexed { index, source ->
                val sourceName = source.text("name").ifBlank { "source[$index]" }
                if (source.text("name").isBlank()) {
                    issues += error("Имя источника не должно быть пустым.")
                }
                if (source.text("jdbcUrl").isBlank()) {
                    issues += error("jdbcUrl источника $sourceName не должен быть пустым.")
                }
                if (source.text("username").isBlank()) {
                    issues += error("Имя пользователя источника $sourceName не должно быть пустым.")
                }
                if (source.text("password").isBlank()) {
                    issues += error("Пароль источника $sourceName не должен быть пустым.")
                }
                val sourceSqlConfigured = source.text("sql").isNotBlank() || source.text("sqlFile").isNotBlank()
                if (!commonSqlConfigured && !sourceSqlConfigured) {
                    issues += error(
                        "Для источника $sourceName не задан SQL-запрос. " +
                            "Укажите commonSql/commonSqlFile или source.sql/sqlFile.",
                    )
                }
            }
        }

        validatePositiveInt(app, "parallelism", "Параметр parallelism должен быть больше 0.")?.let(issues::add)
        validatePositiveInt(app, "fetchSize", "Параметр fetchSize должен быть больше 0.")?.let(issues::add)
        validatePositiveInt(
            app,
            "queryTimeoutSec",
            "Параметр queryTimeoutSec должен быть больше 0, если задан.",
        )?.let(issues::add)
        validatePositiveLong(
            app,
            "progressLogEveryRows",
            "Параметр progressLogEveryRows должен быть больше 0.",
        )?.let(issues::add)
        validatePositiveLong(
            app,
            "maxMergedRows",
            "Параметр maxMergedRows должен быть больше 0, если задан.",
        )?.let(issues::add)

        val target = app.path("target")
        if (target.boolean("enabled")) {
            if (target.text("jdbcUrl").isBlank()) {
                issues += error("jdbcUrl для целевой БД не должен быть пустым.")
            }
            if (target.text("username").isBlank()) {
                issues += error("Имя пользователя для целевой БД не должно быть пустым.")
            }
            if (target.text("password").isBlank()) {
                issues += error("Пароль для целевой БД не должен быть пустым.")
            }
            if (target.text("table").isBlank()) {
                issues += error("Имя целевой таблицы не должно быть пустым.")
            }
        }

        return issues
    }

    private fun validatePositiveInt(node: JsonNode, field: String, message: String): ModuleValidationIssue? {
        val value = node[field] ?: return null
        if (!value.isInt && !value.canConvertToInt()) {
            return null
        }
        return value.asInt().takeIf { it <= 0 }?.let { error(message) }
    }

    private fun validatePositiveLong(node: JsonNode, field: String, message: String): ModuleValidationIssue? {
        val value = node[field] ?: return null
        if (!value.isLong && !value.isInt && !value.canConvertToLong()) {
            return null
        }
        return value.asLong().takeIf { it <= 0L }?.let { error(message) }
    }

    private fun JsonNode.text(field: String): String =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim().orEmpty()

    private fun JsonNode.boolean(field: String): Boolean =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asBoolean() == true

    private fun error(message: String): ModuleValidationIssue =
        ModuleValidationIssue(
            severity = ModuleValidationSeverity.ERROR,
            message = message,
        )
}

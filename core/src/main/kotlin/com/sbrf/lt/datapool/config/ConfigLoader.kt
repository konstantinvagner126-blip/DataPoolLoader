package com.sbrf.lt.datapool.config

import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ErrorMode
import com.sbrf.lt.datapool.model.RootConfig
import com.sbrf.lt.datapool.model.SourceConfig
import com.sbrf.lt.datapool.model.SourceQuotaConfig
import com.sbrf.lt.datapool.model.TargetConfig
import com.sbrf.lt.datapool.model.MergeMode
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path

class ConfigLoader {
    private val mapper: ObjectMapper = YAMLMapper.builder(YAMLFactory())
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .build()

    fun load(path: Path): AppConfig {
        require(Files.exists(path)) { "Файл конфигурации не найден: $path" }

        val root = Files.newBufferedReader(path).use { reader ->
            mapper.readValue(reader, RootConfig::class.java)
        }

        return root.app.validate()
    }

    fun objectMapper(): ObjectMapper = mapper
}

fun AppConfig.validate(): AppConfig {
    val normalizedCommonSql = commonSql.trim()
    val normalizedSources = sources.map { it.normalize() }
    val normalizedQuotas = quotas.map { it.normalize() }
    val normalizedTarget = target.normalize()

    require(fileFormat.equals("csv", ignoreCase = true)) { "Поддерживается только формат CSV." }
    require(normalizedSources.isNotEmpty()) { "Должен быть настроен хотя бы один источник." }
    require(parallelism > 0) { "Параметр parallelism должен быть больше 0." }
    require(fetchSize > 0) { "Параметр fetchSize должен быть больше 0." }
    require(progressLogEveryRows > 0) { "Параметр progressLogEveryRows должен быть больше 0." }
    require(maxMergedRows == null || maxMergedRows > 0) { "Параметр maxMergedRows должен быть больше 0, если задан." }
    require(errorMode == ErrorMode.CONTINUE_ON_ERROR) { "Поддерживается только режим CONTINUE_ON_ERROR." }
    if (normalizedCommonSql.isNotBlank()) {
        require(isSelectOnly(normalizedCommonSql)) { "Общий SQL-запрос должен быть SELECT-запросом." }
    }
    if (normalizedTarget.enabled) {
        require(normalizedTarget.jdbcUrl.isNotBlank()) { "jdbcUrl для целевой БД не должен быть пустым." }
        require(normalizedTarget.username.isNotBlank()) { "Имя пользователя для целевой БД не должно быть пустым." }
        require(normalizedTarget.password.isNotBlank()) { "Пароль для целевой БД не должен быть пустым." }
        require(normalizedTarget.table.isNotBlank()) { "Имя целевой таблицы не должно быть пустым." }
    }

    normalizedSources.forEach { source ->
        require(source.name.isNotBlank()) { "Имя источника не должно быть пустым." }
        require(source.jdbcUrl.isNotBlank()) { "jdbcUrl источника ${source.name} не должен быть пустым." }
        require(source.username.isNotBlank()) { "Имя пользователя источника ${source.name} не должно быть пустым." }
        require(source.password.isNotBlank()) { "Пароль источника ${source.name} не должен быть пустым." }
        val effectiveSql = source.sql ?: normalizedCommonSql.takeIf { it.isNotBlank() }
        require(effectiveSql != null) { "Для источника ${source.name} не задан SQL-запрос. Укажите commonSql или source.sql." }
        require(isSelectOnly(effectiveSql)) { "SQL-запрос источника ${source.name} должен быть SELECT-запросом." }
    }

    if (mergeMode == MergeMode.QUOTA) {
        require(normalizedQuotas.isNotEmpty()) { "Для режима QUOTA должен быть задан список quotas." }
        val configuredSourceNames = normalizedSources.map { it.name }.toSet()
        val quotaSourceNames = normalizedQuotas.map { it.source }
        require(quotaSourceNames.size == quotaSourceNames.toSet().size) { "В quotas найдены дублирующиеся источники." }
        require(quotaSourceNames.toSet() == configuredSourceNames) {
            "В режиме QUOTA список quotas должен содержать все источники ровно по одному разу."
        }
        normalizedQuotas.forEach {
            require(it.percent > 0.0) { "Процент для источника ${it.source} должен быть больше 0." }
        }
        val totalPercent = normalizedQuotas.sumOf { it.percent }
        require(kotlin.math.abs(totalPercent - 100.0) < 0.0001) {
            "Сумма процентов в quotas должна быть равна 100."
        }
    }

    return copy(
        commonSql = normalizedCommonSql,
        sources = normalizedSources,
        quotas = normalizedQuotas,
        target = normalizedTarget,
    )
}

private fun SourceConfig.normalize(): SourceConfig = copy(
    name = name.trim(),
    jdbcUrl = jdbcUrl.trim(),
    username = username.trim(),
    password = password.trim(),
    sql = sql?.trim()?.takeIf { it.isNotEmpty() },
)

private fun TargetConfig.normalize(): TargetConfig = copy(
    jdbcUrl = jdbcUrl.trim(),
    username = username.trim(),
    password = password.trim(),
    table = table.trim(),
)

private fun SourceQuotaConfig.normalize(): SourceQuotaConfig = copy(
    source = source.trim(),
)

private fun isSelectOnly(sql: String): Boolean {
    val normalized = sql
        .trim()
        .replace(Regex("(?s)/\\*.*?\\*/"), " ")
        .lineSequence()
        .map { it.substringBefore("--") }
        .joinToString(" ")
        .trim()
        .lowercase()

    return normalized.startsWith("select ") || normalized.startsWith("with ")
}

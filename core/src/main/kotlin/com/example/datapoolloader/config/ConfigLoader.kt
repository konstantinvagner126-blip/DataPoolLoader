package com.example.datapoolloader.config

import com.example.datapoolloader.model.AppConfig
import com.example.datapoolloader.model.ErrorMode
import com.example.datapoolloader.model.RootConfig
import com.example.datapoolloader.model.SourceConfig
import com.example.datapoolloader.model.TargetConfig
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
        require(Files.exists(path)) { "Config file does not exist: $path" }

        val root = Files.newBufferedReader(path).use { reader ->
            mapper.readValue(reader, RootConfig::class.java)
        }

        return root.app.validate()
    }

    fun objectMapper(): ObjectMapper = mapper
}

fun AppConfig.validate(): AppConfig {
    val normalizedSql = sql.trim()
    val normalizedSources = sources.map { it.normalize() }
    val normalizedTarget = target.normalize()

    require(fileFormat.equals("csv", ignoreCase = true)) { "Only CSV file format is supported." }
    require(normalizedSources.isNotEmpty()) { "At least one source must be configured." }
    require(parallelism > 0) { "parallelism must be greater than 0." }
    require(fetchSize > 0) { "fetchSize must be greater than 0." }
    require(errorMode == ErrorMode.CONTINUE_ON_ERROR) { "Only CONTINUE_ON_ERROR is supported." }
    require(normalizedSql.isNotBlank()) { "Global SQL must not be blank." }
    require(isSelectOnly(normalizedSql)) { "Global SQL must be a SELECT statement." }
    if (normalizedTarget.enabled) {
        require(normalizedTarget.jdbcUrl.isNotBlank()) { "Target jdbcUrl must not be blank." }
        require(normalizedTarget.username.isNotBlank()) { "Target username must not be blank." }
        require(normalizedTarget.password.isNotBlank()) { "Target password must not be blank." }
        require(normalizedTarget.table.isNotBlank()) { "Target table must not be blank." }
    }

    normalizedSources.forEach { source ->
        require(source.name.isNotBlank()) { "Source name must not be blank." }
        require(source.jdbcUrl.isNotBlank()) { "Source ${source.name} jdbcUrl must not be blank." }
        require(source.username.isNotBlank()) { "Source ${source.name} username must not be blank." }
        require(source.password.isNotBlank()) { "Source ${source.name} password must not be blank." }
        source.sql?.let {
            require(isSelectOnly(it)) { "Source ${source.name} SQL must be a SELECT statement." }
        }
    }

    return copy(
        sql = normalizedSql,
        sources = normalizedSources,
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

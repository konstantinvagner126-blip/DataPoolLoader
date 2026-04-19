package com.sbrf.lt.datapool.sqlconsole

import com.sbrf.lt.datapool.config.ValueResolver
import java.nio.file.Path

internal class SqlConsoleConfigSupport {
    fun determineResultType(results: List<RawShardExecutionResult>): SqlConsoleStatementType {
        return if (results.any { it.columns.isNotEmpty() || it.rows.isNotEmpty() }) {
            SqlConsoleStatementType.RESULT_SET
        } else {
            SqlConsoleStatementType.COMMAND
        }
    }

    fun resolveSources(
        config: SqlConsoleConfig,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
    ): List<ResolvedSqlConsoleShardConfig> {
        validateConfig(config)
        val selectedSources = selectSources(config, selectedSourceNames)
        val resolver = ValueResolver.fromFile(credentialsPath)
        return selectedSources.map { resolveSource(it, resolver) }
    }

    fun selectSources(
        config: SqlConsoleConfig,
        selectedSourceNames: List<String> = emptyList(),
    ): List<SqlConsoleSourceConfig> {
        validateConfig(config)
        return selectSourcesInternal(config, selectedSourceNames)
    }

    fun resolveSource(
        source: SqlConsoleSourceConfig,
        resolver: ValueResolver,
    ): ResolvedSqlConsoleShardConfig {
        return ResolvedSqlConsoleShardConfig(
            name = source.name.trim().also { require(it.isNotBlank()) { "Имя shard не должно быть пустым." } },
            jdbcUrl = resolver.resolve(source.jdbcUrl).trim().also {
                require(it.isNotBlank()) { "jdbcUrl для shard ${source.name} не должен быть пустым." }
            },
            username = resolver.resolve(source.username).trim().also {
                require(it.isNotBlank()) { "username для shard ${source.name} не должен быть пустым." }
            },
            password = resolver.resolve(source.password).trim().also {
                require(it.isNotBlank()) { "password для shard ${source.name} не должен быть пустым." }
            },
        )
    }

    fun parseAndValidateStatement(rawSql: String): SqlConsoleStatement {
        val trimmed = rawSql.trim()
        require(trimmed.isNotBlank()) { "SQL-запрос не должен быть пустым." }

        val normalized = trimmed
            .replace(Regex("(?s)/\\*.*?\\*/"), " ")
            .lineSequence()
            .map { it.substringBefore("--") }
            .joinToString(" ")
            .trim()

        val withoutTrailingSemicolon = normalized.removeSuffix(";").trim()
        require(!withoutTrailingSemicolon.contains(";")) {
            "В SQL-консоли разрешен только один SQL-запрос за запуск."
        }

        val leadingKeyword = withoutTrailingSemicolon
            .substringBefore(" ")
            .ifBlank { withoutTrailingSemicolon }
            .uppercase()

        return SqlConsoleStatement(sql = trimmed, leadingKeyword = leadingKeyword)
    }

    private fun validateConfig(config: SqlConsoleConfig) {
        require(config.fetchSize > 0) { "Параметр ui.sqlConsole.fetchSize должен быть больше 0." }
        require(config.maxRowsPerShard > 0) { "Параметр ui.sqlConsole.maxRowsPerShard должен быть больше 0." }
        require(config.queryTimeoutSec == null || config.queryTimeoutSec > 0) {
            "Параметр ui.sqlConsole.queryTimeoutSec должен быть больше 0, если задан."
        }
        require(config.sources.isNotEmpty()) {
            "В конфиге UI не настроены source-подключения. Заполни ui.sqlConsole.sources."
        }
    }

    private fun selectSourcesInternal(
        config: SqlConsoleConfig,
        selectedSourceNames: List<String> = emptyList(),
    ): List<SqlConsoleSourceConfig> {
        val normalizedSelectedSourceNames = selectedSourceNames.map { it.trim() }.filter { it.isNotEmpty() }
        val selectedSources = if (normalizedSelectedSourceNames.isEmpty()) {
            config.sources
        } else {
            val configuredSourceNames = config.sources.map { it.name }.toSet()
            val unknown = normalizedSelectedSourceNames.filter { it !in configuredSourceNames }
            require(unknown.isEmpty()) {
                "В SQL-консоли выбраны неизвестные sources: ${unknown.joinToString(", ")}"
            }
            config.sources.filter { it.name in normalizedSelectedSourceNames }
        }
        require(selectedSources.isNotEmpty()) {
            "В SQL-консоли должен быть выбран хотя бы один source."
        }
        return selectedSources
    }
}

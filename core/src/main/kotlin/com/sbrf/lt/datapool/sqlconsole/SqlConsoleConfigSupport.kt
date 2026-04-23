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

    fun parseAndValidateStatements(rawSql: String): List<SqlConsoleStatement> {
        val trimmed = rawSql.trim()
        require(trimmed.isNotBlank()) { "SQL-запрос не должен быть пустым." }

        val statements = splitStatements(trimmed).map { statementSql ->
            val normalized = normalizeSql(statementSql)
            val leadingKeyword = normalized
                .substringBefore(" ")
                .ifBlank { normalized }
                .uppercase()
            SqlConsoleStatement(sql = statementSql, leadingKeyword = leadingKeyword)
        }

        require(statements.isNotEmpty()) { "SQL-запрос не должен быть пустым." }
        return statements
    }

    fun requiresManualCommit(statements: List<SqlConsoleStatement>): Boolean =
        statements.any { it.leadingKeyword !in READ_ONLY_SQL_KEYWORDS }

    private fun validateConfig(config: SqlConsoleConfig) {
        require(config.fetchSize > 0) { "Параметр ui.sqlConsole.fetchSize должен быть больше 0." }
        require(config.maxRowsPerShard > 0) { "Параметр ui.sqlConsole.maxRowsPerShard должен быть больше 0." }
        require(config.queryTimeoutSec == null || config.queryTimeoutSec > 0) {
            "Параметр ui.sqlConsole.queryTimeoutSec должен быть больше 0, если задан."
        }
        require(config.sources.isNotEmpty()) {
            "В конфиге UI не настроены source-подключения. Заполни ui.sqlConsole.sources."
        }
        val normalizedSourceNames = config.sources.map { it.name.trim() }
        require(normalizedSourceNames.all { it.isNotEmpty() }) {
            "В SQL-консоли имя source не должно быть пустым."
        }
        val duplicateSourceNames = normalizedSourceNames
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateSourceNames.isEmpty()) {
            "В SQL-консоли имена source должны быть уникальны: ${duplicateSourceNames.joinToString(", ")}"
        }
        validateSourceGroups(config.sourceGroups, normalizedSourceNames.toSet())
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

    private fun normalizeSql(sql: String): String =
        sql
            .replace(Regex("(?s)/\\*.*?\\*/"), " ")
            .lineSequence()
            .map { it.substringBefore("--") }
            .joinToString(" ")
            .trim()
            .removeSuffix(";")
            .trim()

    private fun splitStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var inLineComment = false
        var inBlockComment = false

        while (index < sql.length) {
            val char = sql[index]
            val next = sql.getOrNull(index + 1)

            when {
                inLineComment -> {
                    current.append(char)
                    if (char == '\n') {
                        inLineComment = false
                    }
                }

                inBlockComment -> {
                    current.append(char)
                    if (char == '*' && next == '/') {
                        current.append(next)
                        index++
                        inBlockComment = false
                    }
                }

                inSingleQuote -> {
                    current.append(char)
                    if (char == '\'' && next == '\'') {
                        current.append(next)
                        index++
                    } else if (char == '\'') {
                        inSingleQuote = false
                    }
                }

                inDoubleQuote -> {
                    current.append(char)
                    if (char == '"' && next == '"') {
                        current.append(next)
                        index++
                    } else if (char == '"') {
                        inDoubleQuote = false
                    }
                }

                char == '-' && next == '-' -> {
                    current.append(char).append(next)
                    index++
                    inLineComment = true
                }

                char == '/' && next == '*' -> {
                    current.append(char).append(next)
                    index++
                    inBlockComment = true
                }

                char == '\'' -> {
                    current.append(char)
                    inSingleQuote = true
                }

                char == '"' -> {
                    current.append(char)
                    inDoubleQuote = true
                }

                char == ';' -> {
                    current
                        .toString()
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(statements::add)
                    current.clear()
                }

                else -> current.append(char)
            }

            index++
        }

        current
            .toString()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let(statements::add)

        return statements
    }
}

private fun validateSourceGroups(
    sourceGroups: List<SqlConsoleSourceGroupConfig>,
    configuredSourceNames: Set<String>,
) {
    val normalizedGroupNames = sourceGroups.map { it.name.trim() }
    require(normalizedGroupNames.all { it.isNotEmpty() }) {
        "В SQL-консоли имя группы source не должно быть пустым."
    }
    val duplicateGroupNames = normalizedGroupNames
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    require(duplicateGroupNames.isEmpty()) {
        "В SQL-консоли имена групп должны быть уникальны: ${duplicateGroupNames.joinToString(", ")}"
    }

    sourceGroups.forEach { group ->
        val normalizedSourceNames = group.sourceNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        require(normalizedSourceNames.isNotEmpty()) {
            "Группа SQL-консоли '${group.name.trim()}' должна содержать хотя бы один source."
        }
        val unknownSourceNames = normalizedSourceNames.filter { it !in configuredSourceNames }
        require(unknownSourceNames.isEmpty()) {
            "Группа SQL-консоли '${group.name.trim()}' содержит неизвестные source: ${unknownSourceNames.joinToString(", ")}"
        }
    }
}

private val READ_ONLY_SQL_KEYWORDS = setOf(
    "SELECT",
    "WITH",
    "SHOW",
    "DESCRIBE",
    "DESC",
    "EXPLAIN",
    "VALUES",
)

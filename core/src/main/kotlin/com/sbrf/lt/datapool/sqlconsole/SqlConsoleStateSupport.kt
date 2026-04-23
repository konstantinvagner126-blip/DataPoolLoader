package com.sbrf.lt.datapool.sqlconsole

internal class SqlConsoleStateSupport {
    fun info(config: SqlConsoleConfig): SqlConsoleInfo =
        SqlConsoleInfo(
            configured = config.sourceCatalog.isNotEmpty(),
            sourceCatalog = config.sourceCatalog.map { source ->
                SqlConsoleSourceCatalogEntry(name = source.name.trim())
            },
            groups = buildRuntimeSourceGroups(config),
            maxRowsPerShard = config.maxRowsPerShard,
            queryTimeoutSec = config.queryTimeoutSec,
        )

    fun updateSettings(
        currentConfig: SqlConsoleConfig,
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): SqlConsoleConfig {
        require(maxRowsPerShard > 0) { "Лимит строк на source должен быть больше 0." }
        require(queryTimeoutSec == null || queryTimeoutSec > 0) {
            "Таймаут запроса на source должен быть больше 0, если задан."
        }
        return currentConfig.copy(
            maxRowsPerShard = maxRowsPerShard,
            queryTimeoutSec = queryTimeoutSec,
        )
    }
}

private fun buildRuntimeSourceGroups(config: SqlConsoleConfig): List<SqlConsoleSourceGroup> {
    val configuredGroups = config.groups.map { group ->
        SqlConsoleSourceGroup(
            name = group.name.trim(),
            sources = group.sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
    }
    val groupedSourceNames = configuredGroups.flatMap { it.sources }.toSet()
    val ungroupedSourceNames = config.sourceCatalog
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .filter { it !in groupedSourceNames }
    return configuredGroups + listOfNotNull(
        ungroupedSourceNames.takeIf { it.isNotEmpty() }?.let { sourceNames ->
            SqlConsoleSourceGroup(
                name = "Без группы",
                sources = sourceNames,
                synthetic = true,
            )
        },
    )
}

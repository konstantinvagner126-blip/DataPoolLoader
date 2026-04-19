package com.sbrf.lt.datapool.sqlconsole

import java.nio.file.Path

internal class SqlConsoleMetadataSupport(
    private val configSupport: SqlConsoleConfigSupport,
    private val objectSearcher: ShardSqlObjectSearcher,
) {
    fun searchObjects(
        config: SqlConsoleConfig,
        rawQuery: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        maxObjectsPerSource: Int,
    ): SqlConsoleDatabaseObjectSearchResult {
        val query = rawQuery.trim()
        require(query.length >= 2) {
            "Укажи минимум 2 символа для поиска объектов БД."
        }
        require(maxObjectsPerSource in 1..100) {
            "Лимит найденных объектов должен быть в диапазоне 1..100."
        }
        val resolvedSources = configSupport.resolveSources(
            config = config,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
        )
        val sourceResults = resolvedSources.map { shard ->
            runCatching {
                val searchResult = objectSearcher.searchObjects(shard, query, maxObjectsPerSource)
                SqlConsoleDatabaseObjectSourceResult(
                    sourceName = shard.name,
                    status = "SUCCESS",
                    objects = searchResult.objects,
                    truncated = searchResult.truncated,
                )
            }.getOrElse { error ->
                SqlConsoleDatabaseObjectSourceResult(
                    sourceName = shard.name,
                    status = "FAILED",
                    errorMessage = error.message ?: "Не удалось получить метаданные объектов БД.",
                )
            }
        }
        return SqlConsoleDatabaseObjectSearchResult(
            query = query,
            sourceResults = sourceResults,
            maxObjectsPerSource = maxObjectsPerSource,
        )
    }
}

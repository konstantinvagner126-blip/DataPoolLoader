package com.sbrf.lt.datapool.sqlconsole

import java.nio.file.Path

internal class SqlConsoleMetadataSupport(
    private val configSupport: SqlConsoleConfigSupport,
    private val objectSearcher: ShardSqlObjectSearcher,
    private val objectInspector: ShardSqlObjectInspector,
    private val objectColumnLoader: ShardSqlObjectColumnLoader,
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

    fun inspectObject(
        config: SqlConsoleConfig,
        sourceName: String,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
        credentialsPath: Path?,
    ): SqlConsoleDatabaseObjectInspector {
        require(sourceName.isNotBlank()) {
            "Укажи source для просмотра объекта БД."
        }
        require(schemaName.isNotBlank()) {
            "Укажи схему объекта БД."
        }
        require(objectName.isNotBlank()) {
            "Укажи имя объекта БД."
        }
        val shard = configSupport.resolveSources(
            config = config,
            credentialsPath = credentialsPath,
            selectedSourceNames = listOf(sourceName),
        ).singleOrNull()
            ?: error("Не удалось разрешить source '$sourceName' для просмотра объекта БД.")
        return objectInspector.inspectObject(
            shard = shard,
            schemaName = schemaName,
            objectName = objectName,
            objectType = objectType,
        )
    }

    fun loadObjectColumns(
        config: SqlConsoleConfig,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
    ): SqlConsoleDatabaseObjectColumnLookupResult {
        require(schemaName.isNotBlank()) {
            "Укажи схему объекта БД."
        }
        require(objectName.isNotBlank()) {
            "Укажи имя объекта БД."
        }
        val resolvedSources = configSupport.resolveSources(
            config = config,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
        )
        val sourceResults = resolvedSources.map { shard ->
            runCatching {
                SqlConsoleDatabaseObjectColumnSourceResult(
                    sourceName = shard.name,
                    status = "SUCCESS",
                    columns = objectColumnLoader.loadObjectColumns(
                        shard = shard,
                        schemaName = schemaName,
                        objectName = objectName,
                        objectType = objectType,
                    ),
                )
            }.getOrElse { error ->
                SqlConsoleDatabaseObjectColumnSourceResult(
                    sourceName = shard.name,
                    status = "FAILED",
                    errorMessage = error.message ?: "Не удалось получить колонки объекта БД.",
                )
            }
        }
        return SqlConsoleDatabaseObjectColumnLookupResult(
            schemaName = schemaName,
            objectName = objectName,
            objectType = objectType,
            sourceResults = sourceResults,
        )
    }
}

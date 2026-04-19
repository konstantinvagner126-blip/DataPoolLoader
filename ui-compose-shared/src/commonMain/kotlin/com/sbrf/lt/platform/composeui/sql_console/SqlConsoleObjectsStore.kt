package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleObjectsStore(
    private val api: SqlConsoleApi,
) {
    suspend fun load(): SqlConsoleObjectsPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return SqlConsoleObjectsPageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message
                    ?: "Не удалось загрузить runtime context экрана объектов БД.",
            )
        }
        val infoResult = runCatching { api.loadInfo() }
        val info = infoResult.getOrNull()
        if (info == null) {
            return SqlConsoleObjectsPageState(
                loading = false,
                runtimeContext = runtimeContext,
                errorMessage = infoResult.exceptionOrNull()?.message ?: "Не удалось загрузить конфигурацию SQL-консоли.",
            )
        }
        val persistedState = runCatching { api.loadState() }
            .getOrDefault(SqlConsoleStateSnapshot(draftSql = "select 1 as check_value"))
        val selectedSources = persistedState.selectedSourceNames
            .filter { it in info.sourceNames }
            .ifEmpty { info.sourceNames }
        return SqlConsoleObjectsPageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            persistedState = persistedState,
            selectedSourceNames = selectedSources,
            favoriteObjects = persistedState.favoriteObjects,
        )
    }

    fun startLoading(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: SqlConsoleObjectsPageState,
        actionName: String,
    ): SqlConsoleObjectsPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

    fun updateQuery(
        current: SqlConsoleObjectsPageState,
        value: String,
    ): SqlConsoleObjectsPageState =
        current.copy(query = value)

    fun updateSelectedSources(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsoleObjectsPageState {
        val next = if (enabled) {
            (current.selectedSourceNames + sourceName).distinct()
        } else {
            current.selectedSourceNames.filterNot { it == sourceName }
        }
        return current.copy(selectedSourceNames = next)
    }

    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState {
        val persistedState = current.persistedState ?: SqlConsoleStateSnapshot(draftSql = "select 1 as check_value")
        val favoriteObject = value.toFavoriteObject(sourceName)
        val nextFavorites = if (current.favoriteObjects.any { it.matches(favoriteObject) }) {
            current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        } else {
            listOf(favoriteObject) + current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        }.take(20)
        return runCatching {
            val savedState = api.saveState(
                persistedState.toUpdate(
                    selectedSourceNames = current.selectedSourceNames,
                    favoriteObjects = nextFavorites,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = if (nextFavorites.any { it.matches(favoriteObject) }) {
                    "Объект добавлен в избранное."
                } else {
                    "Объект убран из избранного."
                },
                persistedState = savedState,
                favoriteObjects = savedState.favoriteObjects,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось обновить избранные объекты.",
                successMessage = null,
            )
        }
    }

    suspend fun openObjectInConsole(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        draftSql: String,
    ): SqlConsoleObjectsPageState {
        val persistedState = current.persistedState ?: SqlConsoleStateSnapshot(draftSql = "select 1 as check_value")
        return runCatching {
            val savedState = api.saveState(
                persistedState.toUpdate(
                    draftSql = draftSql,
                    selectedSourceNames = listOf(sourceName),
                    favoriteObjects = current.favoriteObjects,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "SQL-шаблон подготовлен.",
                persistedState = savedState,
                favoriteObjects = savedState.favoriteObjects,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось подготовить SQL-шаблон.",
                successMessage = null,
            )
        }
    }

    suspend fun search(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState {
        val query = current.query.trim()
        if (query.length < 2) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи минимум 2 символа для поиска объектов БД.",
                successMessage = null,
            )
        }
        return runCatching {
            val response = api.searchObjects(
                SqlConsoleObjectSearchRequest(
                    query = query,
                    selectedSourceNames = current.selectedSourceNames,
                ),
            )
            val foundObjectsCount = response.sourceResults.sumOf { it.objects.size }
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = if (foundObjectsCount > 0) {
                    "Найдено объектов: $foundObjectsCount."
                } else {
                    "По запросу ничего не найдено."
                },
                searchResponse = response,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить поиск объектов БД.",
                successMessage = null,
            )
        }
    }
}

private fun SqlConsoleDatabaseObject.toFavoriteObject(sourceName: String): SqlConsoleFavoriteObject =
    SqlConsoleFavoriteObject(
        sourceName = sourceName,
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
        tableName = tableName,
    )

private fun SqlConsoleStateSnapshot.toUpdate(
    draftSql: String = this.draftSql,
    selectedSourceNames: List<String>,
    favoriteObjects: List<SqlConsoleFavoriteObject>,
): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        executionPolicy = executionPolicy,
        transactionMode = transactionMode,
    )

private fun SqlConsoleFavoriteObject.matches(other: SqlConsoleFavoriteObject): Boolean =
    sourceName == other.sourceName &&
        schemaName == other.schemaName &&
        objectName == other.objectName &&
        objectType == other.objectType &&
        tableName == other.tableName

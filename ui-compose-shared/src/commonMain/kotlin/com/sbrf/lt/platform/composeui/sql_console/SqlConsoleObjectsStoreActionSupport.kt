package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreActionSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState {
        val persistedState = current.persistedState ?: defaultSqlConsoleStateSnapshot()
        val favoriteObject = value.toFavoriteObject(sourceName)
        val nextFavorites = if (current.favoriteObjects.any { it.matches(favoriteObject) }) {
            current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        } else {
            listOf(favoriteObject) + current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        }.take(20)
        return runCatching {
            val savedState = api.saveState(
                persistedState.toStateUpdate(
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
        val persistedState = current.persistedState ?: defaultSqlConsoleStateSnapshot()
        return runCatching {
            val savedState = api.saveState(
                persistedState.toStateUpdate(
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
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить поиск объектов БД.",
                successMessage = null,
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = null,
            )
        }
    }

    suspend fun loadInspector(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        runCatching {
            val response = api.inspectObject(
                SqlConsoleObjectInspectorRequest(
                    sourceName = sourceName,
                    schemaName = value.schemaName,
                    objectName = value.objectName,
                    objectType = value.objectType,
                ),
            )
            current.copy(
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = response,
            )
        }.getOrElse { error ->
            current.copy(
                inspectorLoading = false,
                inspectorErrorMessage = error.message ?: "Не удалось загрузить metadata выбранного объекта.",
                inspectorResponse = null,
            )
        }
}

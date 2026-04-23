package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreFavoriteSupport(
    private val persistenceSupport: SqlConsoleObjectsStorePersistenceSupport,
) {
    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState {
        val favoriteObject = value.toFavoriteObject(sourceName)
        val nextFavorites = if (current.favoriteObjects.any { it.matches(favoriteObject) }) {
            current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        } else {
            listOf(favoriteObject) + current.favoriteObjects.filterNot { it.matches(favoriteObject) }
        }.take(20)
        return runCatching {
            val savedState = persistenceSupport.saveWorkspaceState(
                current = current,
                workspaceId = workspaceId,
                favoriteObjects = nextFavorites,
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
}

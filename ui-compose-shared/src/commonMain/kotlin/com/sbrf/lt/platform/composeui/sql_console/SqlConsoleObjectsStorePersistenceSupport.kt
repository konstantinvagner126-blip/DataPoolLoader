package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStorePersistenceSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun persistState(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
    ): SqlConsoleObjectsPageState {
        return runCatching {
            current.copy(
                persistedState = saveWorkspaceState(
                    current = current,
                    workspaceId = workspaceId,
                ),
            )
        }.getOrElse {
            current
        }
    }

    suspend fun openObjectInConsole(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        draftSql: String,
    ): SqlConsoleObjectsPageState =
        runCatching {
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "SQL-шаблон подготовлен.",
                persistedState = saveWorkspaceState(
                    current = current,
                    workspaceId = workspaceId,
                    draftSql = draftSql,
                    selectedGroupNames = emptyList(),
                    selectedSourceNames = listOf(sourceName),
                ),
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось подготовить SQL-шаблон.",
                successMessage = null,
            )
        }

    suspend fun saveWorkspaceState(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        draftSql: String = current.persistedState?.draftSql ?: defaultSqlConsoleStateSnapshot().draftSql,
        selectedGroupNames: List<String> = current.selectedGroupNames,
        selectedSourceNames: List<String> = current.selectedSourceNames,
        favoriteObjects: List<SqlConsoleFavoriteObject> = current.favoriteObjects,
    ): SqlConsoleStateSnapshot {
        val persistedState = current.persistedState ?: defaultSqlConsoleStateSnapshot()
        return api.saveState(
            persistedState.toStateUpdate(
                draftSql = draftSql,
                selectedGroupNames = selectedGroupNames,
                selectedSourceNames = selectedSourceNames,
                favoriteObjects = favoriteObjects,
            ),
            workspaceId = workspaceId,
        )
    }
}

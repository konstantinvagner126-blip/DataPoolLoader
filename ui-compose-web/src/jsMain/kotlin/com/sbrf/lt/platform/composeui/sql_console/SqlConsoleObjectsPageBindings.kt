package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class SqlConsoleObjectsPageCallbacks(
    val onToggleSource: (String, Boolean) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onToggleFavorite: (String, SqlConsoleDatabaseObject) -> Unit,
    val onOpenSelect: (String, SqlConsoleDatabaseObject) -> Unit,
    val onOpenCount: (String, SqlConsoleDatabaseObject) -> Unit,
)

internal fun sqlConsoleObjectsPageCallbacks(
    store: SqlConsoleObjectsStore,
    scope: CoroutineScope,
    currentState: () -> SqlConsoleObjectsPageState,
    setState: (SqlConsoleObjectsPageState) -> Unit,
): SqlConsoleObjectsPageCallbacks =
    SqlConsoleObjectsPageCallbacks(
        onToggleSource = { sourceName, selected ->
            setState(store.updateSelectedSources(currentState(), sourceName, selected))
        },
        onQueryChange = { query ->
            setState(store.updateQuery(currentState(), query))
        },
        onSearch = {
            scope.launch {
                setState(store.beginAction(currentState(), "search"))
                setState(store.search(currentState()))
            }
        },
        onToggleFavorite = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "toggle-favorite-object"))
                setState(store.toggleFavoriteObject(currentState(), sourceName, dbObject))
            }
        },
        onOpenSelect = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "open-object-select"))
                setState(
                    store.openObjectInConsole(
                        current = currentState(),
                        sourceName = sourceName,
                        draftSql = buildPreviewSql(dbObject),
                    ),
                )
                if (currentState().errorMessage == null) {
                    window.location.href = "/sql-console"
                }
            }
        },
        onOpenCount = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "open-object-count"))
                setState(
                    store.openObjectInConsole(
                        current = currentState(),
                        sourceName = sourceName,
                        draftSql = buildCountSql(dbObject),
                    ),
                )
                if (currentState().errorMessage == null) {
                    window.location.href = "/sql-console"
                }
            }
        },
    )

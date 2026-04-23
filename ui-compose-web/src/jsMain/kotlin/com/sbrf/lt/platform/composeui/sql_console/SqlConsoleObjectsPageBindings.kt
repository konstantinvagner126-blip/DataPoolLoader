package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class SqlConsoleObjectsPageCallbacks(
    val onToggleSourceGroup: (SqlConsoleSourceGroup, Boolean) -> Unit,
    val onToggleSource: (String, Boolean) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onOpenInspector: (String, SqlConsoleDatabaseObject) -> Unit,
    val onToggleFavorite: (String, SqlConsoleDatabaseObject) -> Unit,
    val onOpenSelect: (String, SqlConsoleDatabaseObject) -> Unit,
    val onOpenCount: (String, SqlConsoleDatabaseObject) -> Unit,
)

internal fun sqlConsoleObjectsPageCallbacks(
    store: SqlConsoleObjectsStore,
    workspaceId: String,
    scope: CoroutineScope,
    currentState: () -> SqlConsoleObjectsPageState,
    setState: (SqlConsoleObjectsPageState) -> Unit,
): SqlConsoleObjectsPageCallbacks =
    SqlConsoleObjectsPageCallbacks(
        onToggleSourceGroup = { group, selected ->
            setState(store.updateSelectedSourceGroup(currentState(), group, selected))
        },
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
        onOpenInspector = { sourceName, dbObject ->
            window.location.href = buildObjectInspectorHref(sourceName, dbObject, workspaceId)
        },
        onToggleFavorite = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "toggle-favorite-object"))
                setState(store.toggleFavoriteObject(currentState(), workspaceId, sourceName, dbObject))
            }
        },
        onOpenSelect = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "open-object-select"))
                setState(
                    store.openObjectInConsole(
                        current = currentState(),
                        workspaceId = workspaceId,
                        sourceName = sourceName,
                        draftSql = buildPreviewSql(dbObject),
                    ),
                )
                if (currentState().errorMessage == null) {
                    window.location.href = buildSqlConsoleWorkspaceHref(workspaceId)
                }
            }
        },
        onOpenCount = { sourceName, dbObject ->
            scope.launch {
                setState(store.beginAction(currentState(), "open-object-count"))
                setState(
                    store.openObjectInConsole(
                        current = currentState(),
                        workspaceId = workspaceId,
                        sourceName = sourceName,
                        draftSql = buildCountSql(dbObject),
                    ),
                )
                if (currentState().errorMessage == null) {
                    window.location.href = buildSqlConsoleWorkspaceHref(workspaceId)
                }
            }
        },
    )

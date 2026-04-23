package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SQL_CONSOLE_MONACO_NAMESPACE = "ComposeMonaco"

@Composable
internal fun SqlConsoleMonacoObjectNavigationEffect(
    api: SqlConsoleApi,
    scope: CoroutineScope,
    currentState: () -> SqlConsolePageState,
    setState: (SqlConsolePageState) -> Unit,
) {
    DisposableEffect(api, scope) {
        applySqlConsoleMonacoObjectNavigationHandlers(
            openInspector = { target, requestedTab ->
                scope.launch {
                    openSqlConsoleObjectInspectorInNewTab(
                        api = api,
                        currentState = currentState,
                        setState = setState,
                        target = target,
                        requestedTab = requestedTab,
                    )
                }
            },
            openSelect = { target ->
                scope.launch {
                    openSqlConsoleObjectSelectInNewTab(
                        api = api,
                        currentState = currentState,
                        setState = setState,
                        target = target,
                    )
                }
            },
        )
        onDispose {
            clearSqlConsoleMonacoObjectNavigationHandlers()
        }
    }
}

private suspend fun openSqlConsoleObjectInspectorInNewTab(
    api: SqlConsoleApi,
    currentState: () -> SqlConsolePageState,
    setState: (SqlConsolePageState) -> Unit,
    target: SqlObjectNavigationTarget,
    requestedTab: String?,
) {
    val targetWorkspaceId = generateSqlConsoleWorkspaceId()
    val sourceState = currentState()
    runCatching {
        api.saveState(sourceState.toNavigationStateUpdate(), workspaceId = targetWorkspaceId)
    }.onSuccess {
        val opened = openHrefInNewTab(
            buildObjectInspectorHref(
                sourceName = target.sourceName,
                dbObject = target.toDatabaseObject(),
                workspaceId = targetWorkspaceId,
                inspectorTab = requestedTab,
            ),
        )
        if (!opened) {
            setState(
                currentState().copy(
                    errorMessage = "Браузер заблокировал открытие вкладки инспектора объекта.",
                    successMessage = null,
                ),
            )
        }
    }.onFailure { error ->
        setState(
            currentState().copy(
                errorMessage = error.message ?: "Не удалось подготовить вкладку инспектора объекта.",
                successMessage = null,
            ),
        )
    }
}

private suspend fun openSqlConsoleObjectSelectInNewTab(
    api: SqlConsoleApi,
    currentState: () -> SqlConsolePageState,
    setState: (SqlConsolePageState) -> Unit,
    target: SqlObjectNavigationTarget,
) {
    if (!supportsRowPreview(target.objectType)) {
        setState(
            currentState().copy(
                errorMessage = "Для этого типа объекта доступен инспектор, но не SELECT-шаблон.",
                successMessage = null,
            ),
        )
        return
    }

    val targetWorkspaceId = generateSqlConsoleWorkspaceId()
    val sourceState = currentState()
    runCatching {
        api.saveState(sourceState.toNavigationStateUpdate(), workspaceId = targetWorkspaceId)
        api.saveState(
            sourceState.toNavigationStateUpdate(
                draftSql = buildPreviewSql(target.toDatabaseObject()),
                selectedGroupNames = emptyList(),
                selectedSourceNames = listOf(target.sourceName),
            ),
            workspaceId = targetWorkspaceId,
        )
    }.onSuccess {
        val opened = openSqlConsoleWorkspaceInNewTab(targetWorkspaceId)
        if (!opened) {
            setState(
                currentState().copy(
                    errorMessage = "Браузер заблокировал открытие новой вкладки SQL-консоли.",
                    successMessage = null,
                ),
            )
        }
    }.onFailure { error ->
        setState(
            currentState().copy(
                errorMessage = error.message ?: "Не удалось подготовить SQL-шаблон в новой вкладке.",
                successMessage = null,
            ),
        )
    }
}

private fun SqlObjectNavigationTarget.toDatabaseObject(): SqlConsoleDatabaseObject =
    SqlConsoleDatabaseObject(
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
    )

private fun applySqlConsoleMonacoObjectNavigationHandlers(
    openInspector: (SqlObjectNavigationTarget, String?) -> Unit,
    openSelect: (SqlObjectNavigationTarget) -> Unit,
) {
    val handlers = js("{}")
    handlers.openInspector = { rawTarget: dynamic, rawTab: dynamic ->
        toSqlObjectNavigationTarget(rawTarget)
            ?.let { target ->
                openInspector(target, rawTab?.toString()?.trim()?.takeIf { it.isNotEmpty() })
            }
    }
    handlers.openSelect = { rawTarget: dynamic ->
        toSqlObjectNavigationTarget(rawTarget)?.let(openSelect)
    }
    val composeMonaco = window.asDynamic()[SQL_CONSOLE_MONACO_NAMESPACE] ?: return
    if (composeMonaco.setSqlObjectNavigationHandlers == undefined) {
        return
    }
    composeMonaco.setSqlObjectNavigationHandlers(handlers)
}

private fun clearSqlConsoleMonacoObjectNavigationHandlers() {
    val composeMonaco = window.asDynamic()[SQL_CONSOLE_MONACO_NAMESPACE] ?: return
    if (composeMonaco.setSqlObjectNavigationHandlers == undefined) {
        return
    }
    composeMonaco.setSqlObjectNavigationHandlers(null)
}

private fun toSqlObjectNavigationTarget(rawTarget: dynamic): SqlObjectNavigationTarget? {
    val sourceName = rawTarget.sourceName?.toString()?.trim().orEmpty()
    val schemaName = rawTarget.schemaName?.toString()?.trim().orEmpty()
    val objectName = rawTarget.objectName?.toString()?.trim().orEmpty()
    val objectType = rawTarget.objectType?.toString()?.trim().orEmpty()
    if (sourceName.isBlank() || schemaName.isBlank() || objectName.isBlank() || objectType.isBlank()) {
        return null
    }
    return SqlObjectNavigationTarget(
        sourceName = sourceName,
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
    )
}

private fun SqlConsolePageState.toNavigationStateUpdate(
    draftSql: String = this.draftSql,
    selectedGroupNames: List<String> = this.selectedGroupNames,
    selectedSourceNames: List<String> = this.selectedSourceNames,
): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedGroupNames = selectedGroupNames,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    )

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleStateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleFavoriteObjectResponse

class SqlConsoleStateService(
    private val stateStore: SqlConsoleStateStore,
) {
    private val lock = Any()
    private var state: PersistedSqlConsoleState = stateStore.load()

    fun currentState(): SqlConsoleStateResponse = synchronized(lock) {
        state.toResponse()
    }

    fun updateState(request: SqlConsoleStateUpdateRequest): SqlConsoleStateResponse = synchronized(lock) {
        state = PersistedSqlConsoleState(
            draftSql = request.draftSql,
            recentQueries = request.recentQueries,
            favoriteQueries = request.favoriteQueries,
            favoriteObjects = request.favoriteObjects.map {
                PersistedSqlConsoleFavoriteObject(
                    sourceName = it.sourceName,
                    schemaName = it.schemaName,
                    objectName = it.objectName,
                    objectType = it.objectType,
                    tableName = it.tableName,
                )
            },
            selectedSourceNames = request.selectedSourceNames,
            pageSize = request.pageSize,
            strictSafetyEnabled = request.strictSafetyEnabled,
            executionPolicy = "STOP_ON_FIRST_ERROR",
            transactionMode = request.transactionMode,
        ).normalized()
        stateStore.save(state)
        state.toResponse()
    }
}

private fun PersistedSqlConsoleState.toResponse(): SqlConsoleStateResponse = SqlConsoleStateResponse(
    draftSql = draftSql,
    recentQueries = recentQueries,
    favoriteQueries = favoriteQueries,
    favoriteObjects = favoriteObjects.map {
        SqlConsoleFavoriteObjectResponse(
            sourceName = it.sourceName,
            schemaName = it.schemaName,
            objectName = it.objectName,
            objectType = it.objectType,
            tableName = it.tableName,
        )
    },
    selectedSourceNames = selectedSourceNames,
    pageSize = pageSize,
    strictSafetyEnabled = strictSafetyEnabled,
    executionPolicy = executionPolicy,
    transactionMode = transactionMode,
)

package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleStateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest

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
            selectedSourceNames = request.selectedSourceNames,
            pageSize = request.pageSize,
            strictSafetyEnabled = request.strictSafetyEnabled,
        ).normalized()
        stateStore.save(state)
        state.toResponse()
    }
}

private fun PersistedSqlConsoleState.toResponse(): SqlConsoleStateResponse = SqlConsoleStateResponse(
    draftSql = draftSql,
    recentQueries = recentQueries,
    favoriteQueries = favoriteQueries,
    selectedSourceNames = selectedSourceNames,
    pageSize = pageSize,
    strictSafetyEnabled = strictSafetyEnabled,
)

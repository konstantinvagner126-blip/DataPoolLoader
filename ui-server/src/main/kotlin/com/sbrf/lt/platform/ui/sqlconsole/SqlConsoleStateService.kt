package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleStateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleFavoriteObjectResponse
import java.nio.file.Path

class SqlConsoleStateService(
    private val workspaceStore: SqlConsoleWorkspaceStateStore,
    private val preferencesStore: SqlConsolePreferencesStateStore,
) {
    private val lock = Any()
    private var workspaceState: PersistedSqlConsoleWorkspaceState = workspaceStore.load()
    private var preferencesState: PersistedSqlConsolePreferencesState = preferencesStore.load()

    constructor(storageDir: Path) : this(
        workspaceStore = SqlConsoleWorkspaceStateStore(storageDir),
        preferencesStore = SqlConsolePreferencesStateStore(storageDir),
    )

    fun currentState(): SqlConsoleStateResponse = synchronized(lock) {
        toResponse()
    }

    fun updateState(request: SqlConsoleStateUpdateRequest): SqlConsoleStateResponse = synchronized(lock) {
        workspaceState = PersistedSqlConsoleWorkspaceState(
            draftSql = request.draftSql,
            selectedSourceNames = request.selectedSourceNames,
        ).normalized()
        preferencesState = PersistedSqlConsolePreferencesState(
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
            pageSize = request.pageSize,
            strictSafetyEnabled = request.strictSafetyEnabled,
            transactionMode = request.transactionMode,
        ).normalized()
        workspaceStore.save(workspaceState)
        preferencesStore.save(preferencesState)
        toResponse()
    }

    private fun toResponse(): SqlConsoleStateResponse = SqlConsoleStateResponse(
        draftSql = workspaceState.draftSql,
        recentQueries = preferencesState.recentQueries,
        favoriteQueries = preferencesState.favoriteQueries,
        favoriteObjects = preferencesState.favoriteObjects.map {
            SqlConsoleFavoriteObjectResponse(
                sourceName = it.sourceName,
                schemaName = it.schemaName,
                objectName = it.objectName,
                objectType = it.objectType,
                tableName = it.tableName,
            )
        },
        selectedSourceNames = workspaceState.selectedSourceNames,
        pageSize = preferencesState.pageSize,
        strictSafetyEnabled = preferencesState.strictSafetyEnabled,
        executionPolicy = "STOP_ON_FIRST_ERROR",
        transactionMode = preferencesState.transactionMode,
    )
}

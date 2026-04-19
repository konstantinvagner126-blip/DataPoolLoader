package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleStateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleFavoriteObjectResponse
import java.nio.file.Path

class SqlConsoleStateService(
    private val workspaceStore: SqlConsoleWorkspaceStateStore,
    private val libraryStore: SqlConsoleLibraryStateStore,
    private val preferencesStore: SqlConsolePreferencesStateStore,
) {
    private val lock = Any()
    private var workspaceState: PersistedSqlConsoleWorkspaceState = workspaceStore.load()
    private var libraryState: PersistedSqlConsoleLibraryState = libraryStore.load()
    private var preferencesState: PersistedSqlConsolePreferencesState = preferencesStore.load()

    constructor(storageDir: Path) : this(
        workspaceStore = SqlConsoleWorkspaceStateStore(storageDir),
        libraryStore = SqlConsoleLibraryStateStore(storageDir),
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
        libraryState = PersistedSqlConsoleLibraryState(
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
        ).normalized()
        preferencesState = PersistedSqlConsolePreferencesState(
            pageSize = request.pageSize,
            strictSafetyEnabled = request.strictSafetyEnabled,
            transactionMode = request.transactionMode,
        ).normalized()
        workspaceStore.save(workspaceState)
        libraryStore.save(libraryState)
        preferencesStore.save(preferencesState)
        toResponse()
    }

    private fun toResponse(): SqlConsoleStateResponse = SqlConsoleStateResponse(
        draftSql = workspaceState.draftSql,
        recentQueries = libraryState.recentQueries,
        favoriteQueries = libraryState.favoriteQueries,
        favoriteObjects = libraryState.favoriteObjects.map {
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
        transactionMode = preferencesState.transactionMode,
    )
}

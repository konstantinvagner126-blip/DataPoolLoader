package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleStateResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleFavoriteObjectResponse
import java.nio.file.Path
import java.time.Clock

class SqlConsoleStateService internal constructor(
    private val workspaceStore: SqlConsoleWorkspaceStateStore,
    private val libraryStore: SqlConsoleLibraryStateStore,
    private val preferencesStore: SqlConsolePreferencesStateStore,
    private val workspaceRetentionService: SqlConsoleWorkspaceRetentionService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val workspaceStates: MutableMap<String, PersistedSqlConsoleWorkspaceState> = linkedMapOf(
        DEFAULT_SQL_CONSOLE_WORKSPACE_ID to workspaceStore.load(),
    )
    private var libraryState: PersistedSqlConsoleLibraryState = libraryStore.load()
    private var preferencesState: PersistedSqlConsolePreferencesState = preferencesStore.load()

    constructor(storageDir: Path) : this(
        workspaceStore = SqlConsoleWorkspaceStateStore(storageDir),
        libraryStore = SqlConsoleLibraryStateStore(storageDir),
        preferencesStore = SqlConsolePreferencesStateStore(storageDir),
        workspaceRetentionService = SqlConsoleWorkspaceRetentionService(storageDir),
    )

    fun currentState(workspaceId: String? = null): SqlConsoleStateResponse = synchronized(lock) {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        val touchedWorkspaceState = touchWorkspaceState(
            workspaceId = normalizedWorkspaceId,
            workspaceState = currentWorkspaceState(normalizedWorkspaceId),
        )
        toResponse(touchedWorkspaceState)
    }

    fun updateState(
        request: SqlConsoleStateUpdateRequest,
        workspaceId: String? = null,
    ): SqlConsoleStateResponse = synchronized(lock) {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        val workspaceState = PersistedSqlConsoleWorkspaceState(
            draftSql = request.draftSql,
            selectedGroupNames = request.selectedGroupNames,
            selectedSourceNames = request.selectedSourceNames,
        ).normalized()
        val touchedWorkspaceState = touchWorkspaceState(normalizedWorkspaceId, workspaceState)
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
        libraryStore.save(libraryState)
        preferencesStore.save(preferencesState)
        toResponse(touchedWorkspaceState)
    }

    private fun currentWorkspaceState(workspaceId: String?): PersistedSqlConsoleWorkspaceState {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        return workspaceStates.getOrPut(normalizedWorkspaceId) {
            workspaceStore.load(normalizedWorkspaceId)
        }
    }

    private fun touchWorkspaceState(
        workspaceId: String,
        workspaceState: PersistedSqlConsoleWorkspaceState,
    ): PersistedSqlConsoleWorkspaceState {
        val touchedWorkspaceState = workspaceState.touched(clock.instant())
        workspaceStates[workspaceId] = touchedWorkspaceState
        workspaceStore.save(workspaceId, touchedWorkspaceState)
        workspaceRetentionService.markWorkspaceAccessed(workspaceId)
        workspaceRetentionService.cleanupStaleWorkspaceFiles()
        return touchedWorkspaceState
    }

    private fun toResponse(workspaceState: PersistedSqlConsoleWorkspaceState): SqlConsoleStateResponse = SqlConsoleStateResponse(
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
        selectedGroupNames = workspaceState.selectedGroupNames,
        selectedSourceNames = workspaceState.selectedSourceNames,
        pageSize = preferencesState.pageSize,
        strictSafetyEnabled = preferencesState.strictSafetyEnabled,
        transactionMode = preferencesState.transactionMode,
    )
}

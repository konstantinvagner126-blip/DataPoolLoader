package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Legacy combined state SQL-консоли.
 * Оставлен только для чтения старого `sql-console-state.json` при миграции на раздельные state-файлы.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacySqlConsoleState(
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<PersistedSqlConsoleFavoriteObject> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val transactionMode: String = "AUTO_COMMIT",
) {
    fun normalized(): LegacySqlConsoleState {
        val normalizedQueries = recentQueries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(10)
        val normalizedFavorites = favoriteQueries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(15)
        val normalizedFavoriteObjects = favoriteObjects
            .map { it.normalized() }
            .distinctBy { it.sourceName + "\u0001" + it.schemaName + "\u0001" + it.objectName + "\u0001" + it.objectType + "\u0001" + (it.tableName ?: "") }
            .take(20)
        val normalizedSelectedSources = selectedSourceNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val normalizedPageSize = pageSize.takeIf { it in setOf(25, 50, 100) } ?: 50
        val normalizedDraft = draftSql.ifBlank { "select 1 as check_value" }
        val normalizedTransactionMode = when (transactionMode.uppercase()) {
            "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
            else -> "AUTO_COMMIT"
        }
        return copy(
            draftSql = normalizedDraft,
            recentQueries = normalizedQueries,
            favoriteQueries = normalizedFavorites,
            favoriteObjects = normalizedFavoriteObjects,
            selectedSourceNames = normalizedSelectedSources,
            pageSize = normalizedPageSize,
            transactionMode = normalizedTransactionMode,
        )
    }
}

internal fun LegacySqlConsoleState.toWorkspaceState(): PersistedSqlConsoleWorkspaceState =
    PersistedSqlConsoleWorkspaceState(
        draftSql = draftSql,
        selectedSourceNames = selectedSourceNames,
    ).normalized()

internal fun LegacySqlConsoleState.toPreferencesState(): PersistedSqlConsolePreferencesState =
    PersistedSqlConsolePreferencesState(
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    ).normalized()

internal fun LegacySqlConsoleState.toLibraryState(): PersistedSqlConsoleLibraryState =
    PersistedSqlConsoleLibraryState(
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
    ).normalized()

data class PersistedSqlConsoleFavoriteObject(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val tableName: String? = null,
) {
    fun normalized(): PersistedSqlConsoleFavoriteObject =
        copy(
            sourceName = sourceName.trim(),
            schemaName = schemaName.trim(),
            objectName = objectName.trim(),
            objectType = objectType.trim().uppercase(),
            tableName = tableName?.trim()?.takeIf { it.isNotEmpty() },
        )
}

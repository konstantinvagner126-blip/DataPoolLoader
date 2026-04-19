package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistedSqlConsolePreferencesState(
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val favoriteObjects: List<PersistedSqlConsoleFavoriteObject> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val transactionMode: String = "AUTO_COMMIT",
) {
    fun normalized(): PersistedSqlConsolePreferencesState {
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
        val normalizedPageSize = pageSize.takeIf { it in setOf(25, 50, 100) } ?: 50
        val normalizedTransactionMode = when (transactionMode.uppercase()) {
            "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
            else -> "AUTO_COMMIT"
        }
        return copy(
            recentQueries = normalizedQueries,
            favoriteQueries = normalizedFavorites,
            favoriteObjects = normalizedFavoriteObjects,
            pageSize = normalizedPageSize,
            transactionMode = normalizedTransactionMode,
        )
    }
}

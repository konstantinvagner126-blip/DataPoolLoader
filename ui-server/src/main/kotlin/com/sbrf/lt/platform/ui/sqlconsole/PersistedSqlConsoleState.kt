package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Сохраняемое состояние SQL-консоли: черновик запроса, история и выбранные источники.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistedSqlConsoleState(
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val favoriteQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val executionPolicy: String = "STOP_ON_FIRST_ERROR",
    val transactionMode: String = "AUTO_COMMIT",
) {
    fun normalized(): PersistedSqlConsoleState {
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
        val normalizedSelectedSources = selectedSourceNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val normalizedPageSize = pageSize.takeIf { it in setOf(25, 50, 100) } ?: 50
        val normalizedDraft = draftSql.ifBlank { "select 1 as check_value" }
        val normalizedExecutionPolicy = when (executionPolicy.uppercase()) {
            "CONTINUE_ON_ERROR" -> "CONTINUE_ON_ERROR"
            else -> "STOP_ON_FIRST_ERROR"
        }
        val normalizedTransactionMode = when (transactionMode.uppercase()) {
            "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
            else -> "AUTO_COMMIT"
        }
        return copy(
            draftSql = normalizedDraft,
            recentQueries = normalizedQueries,
            favoriteQueries = normalizedFavorites,
            selectedSourceNames = normalizedSelectedSources,
            pageSize = normalizedPageSize,
            executionPolicy = if (
                normalizedTransactionMode == "TRANSACTION_PER_SHARD" &&
                normalizedExecutionPolicy == "CONTINUE_ON_ERROR"
            ) {
                "STOP_ON_FIRST_ERROR"
            } else {
                normalizedExecutionPolicy
            },
            transactionMode = normalizedTransactionMode,
        )
    }
}

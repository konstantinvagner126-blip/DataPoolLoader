package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersistedSqlConsolePreferencesState(
    val pageSize: Int = 50,
    val strictSafetyEnabled: Boolean = false,
    val transactionMode: String = "AUTO_COMMIT",
) {
    fun normalized(): PersistedSqlConsolePreferencesState {
        val normalizedPageSize = pageSize.takeIf { it in setOf(25, 50, 100) } ?: 50
        val normalizedTransactionMode = when (transactionMode.uppercase()) {
            "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
            else -> "AUTO_COMMIT"
        }
        return copy(
            pageSize = normalizedPageSize,
            transactionMode = normalizedTransactionMode,
        )
    }
}

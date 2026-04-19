package com.sbrf.lt.platform.composeui.sql_console

internal const val DEFAULT_SQL_CONSOLE_DRAFT: String = "select 1 as check_value"

internal fun defaultSqlConsoleStateSnapshot(): SqlConsoleStateSnapshot =
    SqlConsoleStateSnapshot(draftSql = DEFAULT_SQL_CONSOLE_DRAFT)

internal fun SqlConsolePageState.toPersistedState(): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    )

internal fun SqlConsoleStateSnapshot.toStateUpdate(
    draftSql: String = this.draftSql,
    selectedSourceNames: List<String>,
    favoriteObjects: List<SqlConsoleFavoriteObject>,
): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        favoriteObjects = favoriteObjects,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        transactionMode = transactionMode,
    )

internal fun rememberQuery(
    current: List<String>,
    sql: String,
    limit: Int,
): List<String> =
    listOf(sql) + current.filterNot { it == sql }.take(limit - 1)

internal fun normalizePageSize(value: Int): Int =
    when (value) {
        25, 50, 100 -> value
        else -> 50
    }

internal fun normalizeTransactionMode(value: String): String =
    when (value.uppercase()) {
        "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
        else -> "AUTO_COMMIT"
    }

internal fun SqlConsoleFavoriteObject.matches(other: SqlConsoleFavoriteObject): Boolean =
    sourceName == other.sourceName &&
        schemaName == other.schemaName &&
        objectName == other.objectName &&
        objectType == other.objectType &&
        tableName == other.tableName

internal fun toggleSelectedSourceNames(
    current: List<String>,
    sourceName: String,
    enabled: Boolean,
): List<String> =
    if (enabled) {
        (current + sourceName).distinct()
    } else {
        current.filterNot { it == sourceName }
    }

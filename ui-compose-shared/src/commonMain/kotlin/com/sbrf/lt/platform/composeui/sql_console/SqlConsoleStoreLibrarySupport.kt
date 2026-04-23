package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreLibrarySupport {
    fun applyRecentQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun applyFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun applyExecutionHistoryEntry(
        current: SqlConsolePageState,
        entry: SqlConsoleExecutionHistoryEntry,
    ): SqlConsolePageState {
        val info = current.info ?: return current.copy(draftSql = entry.sql)
        val knownSourceNames = info.sourceCatalogNames().toSet()
        val validSourceNames = entry.selectedSourceNames.filter { it in knownSourceNames }
        val selectionState = initializeSelectedSourceState(info.groups, validSourceNames)
        return current.copy(
            draftSql = entry.sql,
            selectedSourceNames = selectionState.selectedSourceNames,
            selectedGroupNames = selectionState.selectedGroupNames,
            manuallyIncludedSourceNames = selectionState.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = selectionState.manuallyExcludedSourceNames,
            errorMessage = null,
            successMessage = if (validSourceNames.isEmpty()) {
                "SQL восстановлен из истории выполнения, но исходные источники больше недоступны в текущей конфигурации."
            } else {
                "SQL и набор источников восстановлены из истории выполнения."
            },
        )
    }

    fun rememberFavoriteQuery(current: SqlConsolePageState): SqlConsolePageState {
        val sql = current.draftSql.trim()
        if (sql.isBlank()) {
            return current.copy(errorMessage = "Сначала введи SQL-запрос.", successMessage = null)
        }
        return current.copy(
            errorMessage = null,
            successMessage = "Запрос добавлен в избранное.",
            favoriteQueries = rememberQuery(current.favoriteQueries, sql, limit = 20),
        )
    }

    fun removeFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(
            favoriteQueries = current.favoriteQueries.filterNot { it == value },
            errorMessage = null,
            successMessage = "Запрос убран из избранного.",
        )

    fun removeFavoriteObject(
        current: SqlConsolePageState,
        value: SqlConsoleFavoriteObject,
    ): SqlConsolePageState =
        current.copy(
            favoriteObjects = current.favoriteObjects.filterNot { it.matches(value) },
            errorMessage = null,
            successMessage = "Объект убран из избранного.",
        )

    fun clearRecentQueries(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(
            recentQueries = emptyList(),
            errorMessage = null,
            successMessage = "История последних запросов очищена.",
        )
}

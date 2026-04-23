package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreSearchSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun search(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState {
        val query = current.query.trim()
        if (query.length < 2) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи минимум 2 символа для поиска объектов БД.",
                successMessage = null,
            )
        }
        return runCatching {
            val response = api.searchObjects(
                SqlConsoleObjectSearchRequest(
                    query = query,
                    selectedSourceNames = current.selectedSourceNames,
                ),
            )
            val foundObjectsCount = response.sourceResults.sumOf { it.objects.size }
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = if (foundObjectsCount > 0) {
                    "Найдено объектов: $foundObjectsCount."
                } else {
                    "По запросу ничего не найдено."
                },
                searchResponse = response,
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить поиск объектов БД.",
                successMessage = null,
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = null,
            )
        }
    }
}

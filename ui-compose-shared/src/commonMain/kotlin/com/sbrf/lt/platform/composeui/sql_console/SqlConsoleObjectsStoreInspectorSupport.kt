package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreInspectorSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun loadInspector(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        runCatching {
            val response = api.inspectObject(
                SqlConsoleObjectInspectorRequest(
                    sourceName = sourceName,
                    schemaName = value.schemaName,
                    objectName = value.objectName,
                    objectType = value.objectType,
                ),
            )
            current.copy(
                inspectorLoading = false,
                inspectorErrorMessage = null,
                inspectorResponse = response,
            )
        }.getOrElse { error ->
            current.copy(
                inspectorLoading = false,
                inspectorErrorMessage = error.message ?: "Не удалось загрузить metadata выбранного объекта.",
                inspectorResponse = null,
            )
        }
}

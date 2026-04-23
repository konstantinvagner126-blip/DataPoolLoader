package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window

private const val SQL_CONSOLE_MONACO_MAX_OBJECTS_PER_SOURCE = 8
private const val SQL_CONSOLE_MONACO_CONTEXT_KEY = "__composeSqlConsoleMetadataContext"

internal fun updateSqlConsoleMonacoMetadataContext(state: SqlConsolePageState) {
    val context = js("{}")
    context.selectedSourceNames = state.selectedSourceNames.toTypedArray()
    context.favoriteObjects = state.favoriteObjects.map(::sqlConsoleMonacoFavoriteObject).toTypedArray()
    context.maxObjectsPerSource = SQL_CONSOLE_MONACO_MAX_OBJECTS_PER_SOURCE
    window.asDynamic()[SQL_CONSOLE_MONACO_CONTEXT_KEY] = context
    applySqlConsoleMonacoMetadataContext(context)
}

internal fun clearSqlConsoleMonacoMetadataContext() {
    window.asDynamic()[SQL_CONSOLE_MONACO_CONTEXT_KEY] = null
    applySqlConsoleMonacoMetadataContext(null)
}

private fun applySqlConsoleMonacoMetadataContext(context: dynamic) {
    val composeMonaco = window.asDynamic().ComposeMonaco ?: return
    if (composeMonaco.setSqlMetadataContext == undefined) {
        return
    }
    composeMonaco.setSqlMetadataContext(context)
}

private fun sqlConsoleMonacoFavoriteObject(value: SqlConsoleFavoriteObject): dynamic {
    val favorite = js("{}")
    favorite.sourceName = value.sourceName
    favorite.schemaName = value.schemaName
    favorite.objectName = value.objectName
    favorite.objectType = value.objectType
    favorite.tableName = value.tableName
    return favorite
}

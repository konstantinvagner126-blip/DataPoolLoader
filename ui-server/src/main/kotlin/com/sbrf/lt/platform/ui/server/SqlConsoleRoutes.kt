package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

/**
 * API SQL-консоли: state/settings, metadata, выполнение, экспорт и async polling.
 */
internal fun Route.registerSqlConsoleRoutes(context: UiServerContext) {
    registerSqlConsoleStateRoutes(context)
    registerSqlConsoleMetadataRoutes(context)
    registerSqlConsoleQueryRoutes(context)
    registerSqlConsoleExportRoutes(context)
    registerSqlConsoleAsyncQueryRoutes(context)
}

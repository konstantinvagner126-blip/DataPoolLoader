package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.routing.Route
import org.slf4j.LoggerFactory

/**
 * API DB-режима: каталог модулей, личный черновик, запуск и import-flow.
 */
internal fun Route.registerDatabaseRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    val logger = LoggerFactory.getLogger("DatabaseRoutes")
    registerDatabaseCleanupRoutes(context, mapper, logger)
    registerDatabaseSyncRoutes(context, mapper, logger)
    registerDatabaseModuleRoutes(context)
}

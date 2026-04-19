package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService

internal fun UiServerContext.requireDatabaseRuntimeContext(
    requireMaintenanceInactive: Boolean = false,
): UiRuntimeContext {
    val runtimeContext = currentRuntimeContext()
    if (requireMaintenanceInactive) {
        requireDatabaseMaintenanceIsInactive()
    }
    requireDatabaseMode(runtimeContext)
    return runtimeContext
}

internal fun UiServerContext.requireDatabaseRouteCleanupService(runtimeContext: UiRuntimeContext): DatabaseRunHistoryCleanupService {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseRunHistoryCleanupService()) {
        "Сервис cleanup истории запусков для режима базы данных не настроен."
    }
}

internal fun UiServerContext.previewDatabaseRunHistoryCleanup(
    disableSafeguard: Boolean,
): DatabaseRunHistoryCleanupPreviewResponse {
    val runtimeContext = requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
    return requireDatabaseRouteCleanupService(runtimeContext).previewCleanup(disableSafeguard = disableSafeguard)
}

internal fun UiServerContext.executeDatabaseRunHistoryCleanup(
    disableSafeguard: Boolean,
): DatabaseRunHistoryCleanupResultResponse {
    val runtimeContext = requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
    return requireDatabaseRouteCleanupService(runtimeContext).executeCleanup(disableSafeguard = disableSafeguard)
}

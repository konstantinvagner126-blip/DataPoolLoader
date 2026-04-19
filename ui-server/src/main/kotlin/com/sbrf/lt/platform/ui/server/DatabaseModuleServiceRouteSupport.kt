package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations

internal fun UiServerContext.requireDatabaseRouteBackend(runtimeContext: UiRuntimeContext): DatabaseModuleBackend {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleBackend()) {
        "Сервис работы с модулями из базы данных не настроен."
    }
}

internal fun UiServerContext.requireDatabaseRouteStore(runtimeContext: UiRuntimeContext): DatabaseModuleRegistryOperations {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleStore()) {
        "Хранилище модулей из базы данных не настроено."
    }
}

internal fun UiServerContext.requireDatabaseRouteRunService(runtimeContext: UiRuntimeContext): DatabaseModuleRunOperations {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleRunService()) {
        "Сервис истории запусков для режима базы данных не настроен."
    }
}

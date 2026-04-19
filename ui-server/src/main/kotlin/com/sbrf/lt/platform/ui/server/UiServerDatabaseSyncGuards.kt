package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncState
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode

internal fun UiServerContext.readSyncStateSafely(): ModuleSyncState =
    runCatching {
        val runtimeContext = currentRuntimeContext()
        val syncService = currentModuleSyncService()
        if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && syncService != null) {
            syncService.currentSyncState()
        } else {
            ModuleSyncState()
        }
    }.getOrElse {
        ModuleSyncState()
    }

internal fun UiServerContext.requireDatabaseMaintenanceIsInactive() {
    if (readSyncStateSafely().maintenanceMode) {
        conflict("Работа с DB-модулями временно недоступна: идет массовый импорт модулей в БД.")
    }
}

internal fun UiServerContext.requireDatabaseModuleIsNotSyncing(moduleCode: String) {
    val activeSync = readSyncStateSafely().activeSingleSync(moduleCode)
    if (activeSync != null) {
        val startedBy = activeSync.startedByActorDisplayName ?: activeSync.startedByActorId
        val startedAt = activeSync.startedAt
        conflict(
            buildString {
                append("Импорт модуля '$moduleCode' уже выполняется")
                if (!startedBy.isNullOrBlank()) {
                    append(" пользователем $startedBy")
                }
                append(". Начало: $startedAt.")
            },
        )
    }
}

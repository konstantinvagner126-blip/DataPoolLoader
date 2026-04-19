package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncState
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.module.backend.ModuleActor

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

internal fun UiServerContext.databaseModeUnavailableMessage(runtimeContext: UiRuntimeContext): String =
    buildString {
        append("Режим базы данных сейчас недоступен.")
        runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
            append(" Причина: ")
            append(reason)
        }
    }

internal fun UiServerContext.requireDatabaseMode(runtimeContext: UiRuntimeContext) {
    if (runtimeContext.effectiveMode != UiModuleStoreMode.DATABASE) {
        serviceUnavailable(databaseModeUnavailableMessage(runtimeContext))
    }
}

internal fun UiServerContext.requireDatabaseActorId(runtimeContext: UiRuntimeContext): String =
    runtimeContext.actor.actorId ?: conflict("Не удалось определить пользователя для режима базы данных.")

internal fun UiServerContext.requireDatabaseActorSource(runtimeContext: UiRuntimeContext): String =
    runtimeContext.actor.actorSource ?: conflict("Не удалось определить источник пользователя для режима базы данных.")

internal fun UiServerContext.requireDatabaseActor(runtimeContext: UiRuntimeContext): ModuleActor =
    ModuleActor(
        actorId = requireDatabaseActorId(runtimeContext),
        actorSource = requireDatabaseActorSource(runtimeContext),
        actorDisplayName = runtimeContext.actor.actorDisplayName,
    )

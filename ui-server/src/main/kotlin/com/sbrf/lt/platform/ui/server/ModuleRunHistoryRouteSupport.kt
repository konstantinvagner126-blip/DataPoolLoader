package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService

internal fun UiServerContext.requireRunHistoryService(
    storageMode: String,
    runtimeContext: UiRuntimeContext,
): Pair<ModuleRunHistoryService, ModuleActor?> =
    when (storageMode.lowercase()) {
        "files" -> {
            if (runtimeContext.effectiveMode != UiModuleStoreMode.FILES) {
                conflict("Страница файловых модулей доступна только в режиме Файлы.")
            }
            filesModuleRunHistoryService to null
        }
        "database" -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            val service = requireNotNull(currentDatabaseModuleRunHistoryService()) {
                "Сервис истории запусков для режима базы данных не настроен."
            }
            service to requireDatabaseActor(runtimeContext)
        }
        else -> badRequest("Неизвестный режим хранения '$storageMode'.")
    }

package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import io.ktor.server.application.ApplicationCall

internal data class ModuleRunRouteContext(
    val moduleId: String,
    val service: ModuleRunHistoryService,
    val actor: ModuleActor?,
)

internal data class ModuleRunDetailsRouteContext(
    val moduleId: String,
    val runId: String,
    val service: ModuleRunHistoryService,
    val actor: ModuleActor?,
)

internal fun UiServerContext.requireModuleRunRouteContext(call: ApplicationCall): ModuleRunRouteContext {
    val runtimeContext = currentRuntimeContext()
    val storageMode = call.requireRouteParam("storage")
    val moduleId = call.requireRouteParam("id")
    val (service, actor) = requireRunHistoryService(storageMode, runtimeContext)
    return ModuleRunRouteContext(
        moduleId = moduleId,
        service = service,
        actor = actor,
    )
}

internal fun UiServerContext.requireModuleRunDetailsRouteContext(call: ApplicationCall): ModuleRunDetailsRouteContext {
    val routeContext = requireModuleRunRouteContext(call)
    return ModuleRunDetailsRouteContext(
        moduleId = routeContext.moduleId,
        runId = call.requireRouteParam("runId"),
        service = routeContext.service,
        actor = routeContext.actor,
    )
}

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

internal fun ApplicationCall.requireRouteParam(name: String): String =
    parameters[name]?.takeIf { it.isNotBlank() }
        ?: badRequest("В route отсутствует обязательный параметр '$name'.")

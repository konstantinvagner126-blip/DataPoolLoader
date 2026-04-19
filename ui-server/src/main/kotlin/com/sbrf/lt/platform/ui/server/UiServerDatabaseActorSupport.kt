package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.module.backend.ModuleActor

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

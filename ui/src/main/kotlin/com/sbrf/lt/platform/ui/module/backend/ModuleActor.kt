package com.sbrf.lt.platform.ui.module.backend

/**
 * Технический контекст пользователя для storage-операций редактора модуля.
 */
data class ModuleActor(
    val actorId: String,
    val actorSource: String,
    val actorDisplayName: String? = null,
)

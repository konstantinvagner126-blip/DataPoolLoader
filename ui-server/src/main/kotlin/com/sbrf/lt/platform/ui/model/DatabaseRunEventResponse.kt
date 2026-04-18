package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Одно событие таймлайна DB-запуска, сохраненное в `module_run_event`.
 */
data class DatabaseRunEventResponse(
    val runEventId: String,
    val seqNo: Int,
    val createdAt: Instant,
    val stage: String,
    val eventType: String,
    val severity: String,
    val sourceName: String? = null,
    val message: String,
    val payloadJson: Map<String, Any?> = emptyMap(),
)

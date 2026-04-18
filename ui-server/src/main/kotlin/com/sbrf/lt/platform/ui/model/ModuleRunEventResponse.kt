package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Одно событие таймлайна запуска модуля.
 */
data class ModuleRunEventResponse(
    val runEventId: String? = null,
    val seqNo: Int,
    val timestamp: Instant? = null,
    val stage: String? = null,
    val eventType: String,
    val severity: String,
    val sourceName: String? = null,
    val message: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
)

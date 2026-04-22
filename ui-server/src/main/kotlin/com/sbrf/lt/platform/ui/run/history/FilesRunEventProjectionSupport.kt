package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunEventResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot

internal fun projectFilesRunEvents(run: UiRunSnapshot): List<ModuleRunEventResponse> =
    run.events.mapIndexedNotNull { index, event ->
        val eventType = detectFilesEventType(event) ?: return@mapIndexedNotNull null
        ModuleRunEventResponse(
            seqNo = index + 1,
            timestamp = event.eventTimestamp(),
            stage = filesStageFor(eventType),
            eventType = eventType,
            severity = filesSeverityFor(eventType, event),
            sourceName = event.eventSourceName(),
            message = filesMessageFor(eventType, event),
            payload = event,
        )
    }

package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import java.time.Instant

/**
 * Runtime-контекст одного DB-запуска, используемый для записи history/event-ов.
 */
data class DatabaseModuleRunContext(
    val runId: String,
    val runtimeSnapshot: RuntimeModuleSnapshot,
    val actorId: String,
    val actorSource: String,
    val actorDisplayName: String?,
    val requestedAt: Instant,
    val sourceOrder: Map<String, Int>,
    val sourceStates: MutableMap<String, DatabaseRunSourceState> = linkedMapOf(),
    val artifactRefsByFileName: MutableMap<String, DatabaseRunArtifactRef> = linkedMapOf(),
    var nextSeqNo: Int = 0,
    var runCreated: Boolean = false,
    var targetStatus: String = "PENDING",
    var targetRowsLoaded: Long? = null,
)

package com.sbrf.lt.platform.ui.sync

import java.time.Instant

/**
 * Активный import-run в DB-режиме: либо full sync, либо точечный import конкретного модуля.
 */
data class ActiveModuleSyncRun(
    val syncRunId: String,
    val scope: String,
    val startedAt: Instant,
    val moduleCode: String? = null,
    val startedByActorId: String? = null,
    val startedByActorSource: String? = null,
    val startedByActorDisplayName: String? = null,
)

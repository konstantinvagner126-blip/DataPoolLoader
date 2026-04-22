package com.sbrf.lt.platform.ui.run

import java.time.Instant
import java.time.temporal.ChronoUnit

internal class OutputRetentionPolicySupport(
    private val retentionDays: Int,
) {
    fun cleanupCutoff(now: Instant = Instant.now()): Instant =
        now.minus(retentionDays.toLong(), ChronoUnit.DAYS)
}

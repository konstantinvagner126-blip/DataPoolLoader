package com.sbrf.lt.platform.ui.run

internal class OutputRetentionRunNormalizationSupport {
    fun normalizeRuns(candidates: List<OutputRetentionRunRef>): List<OutputRetentionRunRef> =
        candidates.mapNotNull { candidate ->
            val trimmed = candidate.outputDir.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                candidate.copy(outputDir = trimmed)
            }
        }
}

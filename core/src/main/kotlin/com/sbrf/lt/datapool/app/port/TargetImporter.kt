package com.sbrf.lt.datapool.app.port

import com.sbrf.lt.datapool.model.TargetConfig
import com.sbrf.lt.datapool.model.TargetLoadSummary
import java.nio.file.Path

/**
 * Порт загрузки итогового merged-файла в целевую систему.
 */
fun interface TargetImporter {
    fun importCsv(
        target: TargetConfig,
        resolvedJdbcUrl: String,
        resolvedUsername: String,
        mergedFile: Path,
        columns: List<String>,
        expectedRowCount: Long,
        resolvedPassword: String,
    ): TargetLoadSummary
}

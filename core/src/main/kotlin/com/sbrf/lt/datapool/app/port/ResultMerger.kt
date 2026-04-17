package com.sbrf.lt.datapool.app.port

import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.MergeResult
import com.sbrf.lt.datapool.model.SourceExecutionResult
import java.nio.file.Path

/**
 * Порт объединения выгруженных source-результатов в итоговый merged-файл.
 */
fun interface ResultMerger {
    fun merge(
        successfulSources: List<SourceExecutionResult>,
        appConfig: AppConfig,
        outputFile: Path,
    ): MergeResult
}

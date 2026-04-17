package com.sbrf.lt.datapool.app.port

import com.sbrf.lt.datapool.model.ExportTask
import com.sbrf.lt.datapool.model.SourceExecutionResult

/**
 * Порт выгрузки одного источника данных во временный файл.
 */
fun interface SourceExporter {
    fun export(task: ExportTask): SourceExecutionResult
}

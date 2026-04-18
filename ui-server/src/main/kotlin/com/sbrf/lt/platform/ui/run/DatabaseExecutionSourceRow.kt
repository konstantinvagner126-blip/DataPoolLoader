package com.sbrf.lt.platform.ui.run

/**
 * Исходные данные DB-модуля, достаточные для создания execution snapshot и runtime snapshot.
 */
internal data class DatabaseExecutionSourceRow(
    val moduleId: String,
    val moduleCode: String,
    val title: String,
    val configText: String,
    val sourceKind: String,
    val sourceRevisionId: String?,
    val sourceWorkingCopyId: String?,
    val workingCopyJson: String?,
)

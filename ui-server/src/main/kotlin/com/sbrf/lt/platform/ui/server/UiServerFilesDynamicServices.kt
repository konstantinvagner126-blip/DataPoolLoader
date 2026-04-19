package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.appsRootPath
import com.sbrf.lt.platform.ui.run.FilesOutputRetentionService
import com.sbrf.lt.platform.ui.run.FilesRunHistoryCleanupService

internal fun UiServerContext.currentFilesRunHistoryCleanupService(): FilesRunHistoryCleanupService =
    FilesRunHistoryCleanupService(filesRunHistoryMaintenance)

internal fun UiServerContext.currentFilesOutputRetentionService(): FilesOutputRetentionService =
    FilesOutputRetentionService(
        runManager = filesRunService,
        retentionDays = currentRuntimeUiConfig().outputRetention.retentionDays,
        keepMinRunsPerModule = currentRuntimeUiConfig().outputRetention.keepMinRunsPerModule,
    )

internal fun UiServerContext.currentAppsRootOrFail(): java.nio.file.Path =
    currentRuntimeUiConfig().appsRootPath() ?: serviceUnavailable("Путь к каталогу apps не настроен.")

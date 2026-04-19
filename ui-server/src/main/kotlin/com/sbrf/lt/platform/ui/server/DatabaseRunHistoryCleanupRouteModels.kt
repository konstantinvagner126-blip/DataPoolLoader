package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse

internal fun DatabaseRunHistoryCleanupPreviewResponse.toCommonCurrentStorageModules(): List<CurrentStorageModuleResponse> =
    currentTopModules.map { module ->
        CurrentStorageModuleResponse(
            moduleCode = module.moduleCode,
            currentRunsCount = module.currentRunsCount,
            currentStorageBytes = module.currentStorageBytes,
            currentOutputDirs = module.currentOutputDirs,
            oldestRequestedAt = module.oldestRequestedAt,
            newestRequestedAt = module.newestRequestedAt,
        )
    }

internal fun DatabaseRunHistoryCleanupPreviewResponse.toCommonCleanupModules(): List<RunHistoryCleanupModuleResponse> =
    modules.map { module ->
        RunHistoryCleanupModuleResponse(
            moduleCode = module.moduleCode,
            totalRunsToDelete = module.totalRunsToDelete,
            oldestRequestedAt = module.oldestRequestedAt,
            newestRequestedAt = module.newestRequestedAt,
        )
    }

internal fun DatabaseRunHistoryCleanupResultResponse.toCommonCleanupModules(): List<RunHistoryCleanupModuleResponse> =
    modules.map { module ->
        RunHistoryCleanupModuleResponse(
            moduleCode = module.moduleCode,
            totalRunsToDelete = module.totalRunsToDelete,
            oldestRequestedAt = module.oldestRequestedAt,
            newestRequestedAt = module.newestRequestedAt,
        )
    }

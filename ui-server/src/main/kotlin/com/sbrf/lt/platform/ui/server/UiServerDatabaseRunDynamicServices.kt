package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.history.DatabaseModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService

internal fun UiServerContext.currentDatabaseModuleRunService(): DatabaseModuleRunOperations? =
    databaseModuleRunService ?: currentDatabasePostgresConfig()?.let { postgres ->
        val store = currentDatabaseModuleStore() ?: com.sbrf.lt.platform.ui.module.DatabaseModuleStore.fromConfig(postgres)
        val runStore = DatabaseRunStore.fromConfig(postgres)
        DatabaseModuleRunService(
            databaseModuleStore = store,
            executionSource = DatabaseModuleExecutionSource.fromConfig(postgres),
            runExecutionStore = runStore,
            runQueryStore = runStore,
            credentialsProvider = credentialsService,
            activeRunRegistry = databaseModuleActiveRunRegistry,
        )
    }

internal fun UiServerContext.currentDatabaseRunHistoryCleanupService(): DatabaseRunHistoryCleanupService? =
    databaseRunHistoryCleanupService ?: currentDatabasePostgresConfig()?.let { postgres ->
        DatabaseRunHistoryCleanupService(
            runStore = DatabaseRunStore.fromConfig(postgres),
        )
    }

internal fun UiServerContext.currentDatabaseOutputRetentionService(): DatabaseOutputRetentionService? =
    databaseOutputRetentionService ?: currentDatabasePostgresConfig()?.let { postgres ->
        DatabaseOutputRetentionService(
            runStore = DatabaseRunStore.fromConfig(postgres),
            retentionDays = currentRuntimeUiConfig().outputRetention.retentionDays,
            keepMinRunsPerModule = currentRuntimeUiConfig().outputRetention.keepMinRunsPerModule,
        )
    }

internal fun UiServerContext.currentDatabaseModuleRunHistoryService(): ModuleRunHistoryService? =
    databaseModuleRunHistoryService ?: run {
        val backend = currentDatabaseModuleBackend() ?: return@run null
        val runService = currentDatabaseModuleRunService() ?: return@run null
        DatabaseModuleRunHistoryService(backend, runService)
    }

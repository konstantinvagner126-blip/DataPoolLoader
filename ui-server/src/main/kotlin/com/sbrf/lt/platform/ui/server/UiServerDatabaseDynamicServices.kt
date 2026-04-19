package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.isConfigured
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.history.DatabaseModuleRunHistoryService
import com.sbrf.lt.platform.ui.run.history.ModuleRunHistoryService
import com.sbrf.lt.platform.ui.sync.DatabaseModuleSyncImporter

internal fun UiServerContext.currentDatabasePostgresConfig() =
    currentRuntimeUiConfig().moduleStore.postgres.takeIf { it.isConfigured() }

internal fun UiServerContext.currentDatabaseModuleStore() =
    databaseModuleStore ?: currentDatabasePostgresConfig()?.let { DatabaseModuleStore.fromConfig(it) }

internal fun UiServerContext.currentDatabaseModuleBackend(): DatabaseModuleBackend? =
    databaseModuleBackend ?: currentDatabaseModuleStore()?.let { DatabaseModuleBackend(it) }

internal fun UiServerContext.currentModuleSyncService(): ModuleSyncService? =
    moduleSyncService ?: currentDatabasePostgresConfig()?.let { postgres ->
        val store = currentDatabaseModuleStore() ?: DatabaseModuleStore.fromConfig(postgres)
        ModuleSyncService(
            connectionProvider = DriverManagerDatabaseConnectionProvider(
                requireNotNull(postgres.jdbcUrl),
                requireNotNull(postgres.username),
                requireNotNull(postgres.password),
            ),
            moduleRegistryImporter = DatabaseModuleSyncImporter(store),
            schema = postgres.schemaName(),
        )
    }

internal fun UiServerContext.currentDatabaseModuleRunService(): DatabaseModuleRunOperations? =
    databaseModuleRunService ?: currentDatabasePostgresConfig()?.let { postgres ->
        val store = currentDatabaseModuleStore() ?: DatabaseModuleStore.fromConfig(postgres)
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

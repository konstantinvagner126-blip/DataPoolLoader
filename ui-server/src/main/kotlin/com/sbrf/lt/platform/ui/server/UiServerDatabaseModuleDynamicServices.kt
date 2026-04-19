package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.isConfigured
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
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

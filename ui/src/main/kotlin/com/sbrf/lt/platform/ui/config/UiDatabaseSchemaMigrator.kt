package com.sbrf.lt.platform.ui.config

import org.flywaydb.core.Flyway

fun interface UiDatabaseMigrationRunner {
    fun migrate()
}

open class UiDatabaseSchemaMigrator(
    private val runnerFactory: (UiModuleStorePostgresConfig) -> UiDatabaseMigrationRunner = { config ->
        val flyway = Flyway.configure()
            .dataSource(config.jdbcUrl, config.username, config.password)
            .locations("classpath:db/migration")
            .schemas(config.schemaName())
            .defaultSchema(config.schemaName())
            .load()
        UiDatabaseMigrationRunner { flyway.migrate() }
    },
) {
    open fun migrateIfNeeded(uiConfig: UiAppConfig) {
        if (!uiConfig.moduleStore.isDatabaseMode()) {
            return
        }
        migrate(uiConfig.moduleStore.postgres)
    }

    open fun migrate(config: UiModuleStorePostgresConfig) {
        require(config.isConfigured()) {
            "Для database-режима нужно задать ui.moduleStore.postgres.jdbcUrl, username и password."
        }
        runnerFactory(config).migrate()
    }
}

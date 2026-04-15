package com.sbrf.lt.platform.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiDatabaseSchemaMigratorTest {

    @Test
    fun `does not run migration in files mode`() {
        var invoked = false
        val migrator = UiDatabaseSchemaMigrator {
            invoked = true
            UiDatabaseMigrationRunner {}
        }

        migrator.migrateIfNeeded(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.FILES,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                    ),
                ),
            )
        )

        assertTrue(!invoked)
    }

    @Test
    fun `runs migration in database mode`() {
        var migrated = false
        lateinit var postgresConfig: UiModuleStorePostgresConfig
        val migrator = UiDatabaseSchemaMigrator {
            postgresConfig = it
            UiDatabaseMigrationRunner { migrated = true }
        }

        migrator.migrateIfNeeded(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.DATABASE,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                        schema = "custom_registry",
                    ),
                ),
            )
        )

        assertEquals("jdbc:postgresql://localhost:5432/modules", postgresConfig.jdbcUrl)
        assertEquals("custom_registry", postgresConfig.schema)
        assertTrue(migrated)
    }

    @Test
    fun `database mode requires configured postgres connection`() {
        val error = assertFailsWith<IllegalArgumentException> {
            UiDatabaseSchemaMigrator { UiDatabaseMigrationRunner {} }.migrateIfNeeded(
                UiAppConfig(
                    moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
                )
            )
        }

        assertTrue(error.message!!.contains("ui.moduleStore.postgres"))
    }
}

package com.sbrf.lt.platform.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiRuntimeContextServiceTest {

    @Test
    fun `files mode stays in files while still reporting database availability`() {
        val service = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity = UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "PostgreSQL registry доступен.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )

        val context = service.resolve(
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

        assertEquals(UiModuleStoreMode.FILES, context.requestedMode)
        assertEquals(UiModuleStoreMode.FILES, context.effectiveMode)
        assertTrue(context.database.available)
        assertTrue(context.actor.resolved)
        assertNull(context.fallbackReason)
    }

    @Test
    fun `database mode falls back to files when database is unavailable`() {
        val service = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity = UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = false,
                        schema = config.schemaName(),
                        message = "PostgreSQL registry недоступен.",
                        errorMessage = "Connection refused",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )

        val context = service.resolve(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.DATABASE,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                    ),
                ),
            )
        )

        assertEquals(UiModuleStoreMode.DATABASE, context.requestedMode)
        assertEquals(UiModuleStoreMode.FILES, context.effectiveMode)
        assertEquals("Connection refused", context.fallbackReason)
    }

    @Test
    fun `database mode falls back to files when actor is unresolved`() {
        val service = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity? = null
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "PostgreSQL registry доступен.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )

        val context = service.resolve(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.DATABASE,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                    ),
                ),
            )
        )

        assertEquals(UiModuleStoreMode.FILES, context.effectiveMode)
        assertTrue(context.actor.requiresManualInput)
        assertTrue(context.fallbackReason!!.contains("actorId"))
    }

    @Test
    fun `database mode stays active when database and actor are ready`() {
        var migrationInvoked = false
        val service = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity = UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "PostgreSQL registry доступен.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) {
                    migrationInvoked = true
                }
            },
        )

        val context = service.resolve(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.DATABASE,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                    ),
                ),
            )
        )

        assertTrue(migrationInvoked)
        assertEquals(UiModuleStoreMode.DATABASE, context.effectiveMode)
        assertNull(context.fallbackReason)
    }

    @Test
    fun `database mode falls back to files when migration fails`() {
        val service = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity = UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "PostgreSQL registry доступен.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) {
                    error("migration failed")
                }
            },
        )

        val context = service.resolve(
            UiAppConfig(
                moduleStore = UiModuleStoreConfig(
                    mode = UiModuleStoreMode.DATABASE,
                    postgres = UiModuleStorePostgresConfig(
                        jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                        username = "registry_user",
                        password = "registry_pwd",
                    ),
                ),
            )
        )

        assertEquals(UiModuleStoreMode.FILES, context.effectiveMode)
        assertTrue(context.database.errorMessage!!.contains("migration failed"))
    }
}

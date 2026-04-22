package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiConfigTest {

    @Test
    fun `loads ui config from classpath`() {
        val config = object : UiConfigLoader() {
            override fun resolveExternalConfigPath() = null
        }.load()

        assertEquals(8080, config.port)
        assertEquals(UiModuleStoreMode.DATABASE, config.moduleStore.mode)
        assertEquals(UiModuleStorePostgresConfig.DEFAULT_SCHEMA, config.moduleStore.postgres.schemaName())
        assertEquals(1000, config.sqlConsole.fetchSize)
        assertEquals(200, config.sqlConsole.maxRowsPerShard)
        assertEquals("\${LOCAL_MANUAL_DB_JDBC_URL}", config.moduleStore.postgres.jdbcUrl)
    }

    @Test
    fun `configured credentials file has priority in default path resolution`() {
        val path = UiAppConfig(
            defaultCredentialsFile = "/tmp/custom-credentials.properties",
            sqlConsole = SqlConsoleConfig(),
        ).defaultCredentialsPath()

        assertNotNull(path)
        assertEquals("/tmp/custom-credentials.properties", path.toString())
    }

    @Test
    fun `relative credentials file is resolved against external config directory`() {
        val configDir = Files.createTempDirectory("ui-config-dir")

        val path = UiAppConfig(
            defaultCredentialsFile = "credential.properties",
            configBaseDir = configDir.toString(),
            sqlConsole = SqlConsoleConfig(),
        ).defaultCredentialsPath()

        assertNotNull(path)
        assertEquals(configDir.resolve("credential.properties").normalize(), path)
    }

    @Test
    fun `finds credentials in nearest project gradle directory when no explicit path configured`() {
        val root = java.nio.file.Files.createTempDirectory("ui-config-root")
        val credentials = root.resolve("gradle").also { java.nio.file.Files.createDirectories(it) }
            .resolve("credential.properties")
            .apply { java.nio.file.Files.writeString(this, "A=B") }
        val nested = root.resolve("apps/demo").also { java.nio.file.Files.createDirectories(it) }
        val current = java.nio.file.Path.of("").toAbsolutePath().normalize()
        val previous = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", nested.toString())
        val path = com.sbrf.lt.datapool.config.CredentialsFileLocator.find(startDir = nested)
        assertEquals(credentials, path)
        } finally {
            if (previous != null) {
                System.setProperty("user.dir", previous)
            } else {
                System.setProperty("user.dir", current.toString())
            }
        }
    }

    @Test
    fun `loads ui config from external file when explicitly configured`() {
        val configFile = Files.createTempDirectory("ui-config")
            .resolve("application.yml")
            .apply {
                parent?.toFile()?.mkdirs()
                writeText(
                    """
                    ui:
                      port: 9191
                      appsRoot: /tmp/apps-root
                      moduleStore:
                        mode: database
                        postgres:
                          jdbcUrl: jdbc:postgresql://localhost:5432/modules
                          username: registry_user
                          password: registry_pwd
                      defaultCredentialsFile: /tmp/credentials.properties
                    """.trimIndent()
                )
            }

        val config = object : UiConfigLoader() {
            override fun resolveExternalConfigPath() = configFile
        }.load()

        assertEquals(9191, config.port)
        assertEquals("/tmp/apps-root", config.appsRoot)
        assertEquals(UiModuleStoreMode.DATABASE, config.moduleStore.mode)
        assertEquals("jdbc:postgresql://localhost:5432/modules", config.moduleStore.postgres.jdbcUrl)
        assertEquals("registry_user", config.moduleStore.postgres.username)
        assertEquals("registry_pwd", config.moduleStore.postgres.password)
        assertEquals("/tmp/credentials.properties", config.defaultCredentialsFile)
        assertEquals(configFile.parent.toAbsolutePath().normalize().toString(), config.configBaseDir)
    }

    @Test
    fun `module store mode supports lowercase config values`() {
        val configFile = Files.createTempDirectory("ui-db-mode-config")
            .resolve("application.yml")
            .apply {
                parent?.toFile()?.mkdirs()
                writeText(
                    """
                    ui:
                      moduleStore:
                        mode: database
                    """.trimIndent()
                )
            }

        val config = object : UiConfigLoader() {
            override fun resolveExternalConfigPath() = configFile
        }.load()

        assertEquals(UiModuleStoreMode.DATABASE, config.moduleStore.mode)
    }

    @Test
    fun `resolves external config from system property`() {
        val configFile = Files.createTempDirectory("ui-system-config")
            .resolve("custom-ui.yml")
            .apply { writeText("ui:\n  port: 8181\n") }
        val previous = System.getProperty("datapool.ui.config")
        try {
            System.setProperty("datapool.ui.config", configFile.toString())

            val path = UiConfigLoader().resolveExternalConfigPath()

            assertEquals(configFile.toAbsolutePath().normalize(), path)
        } finally {
            if (previous != null) {
                System.setProperty("datapool.ui.config", previous)
            } else {
                System.clearProperty("datapool.ui.config")
            }
        }
    }

    @Test
    fun `loads ui config from packaged sidecar file`() {
        val configFile = Files.createTempDirectory("ui-sidecar-config")
            .resolve("ui-application.yml")
            .apply {
                writeText(
                    """
                    ui:
                      port: 9393
                      appsRoot: /tmp/sidecar-apps
                    """.trimIndent()
                )
            }

        val config = object : UiConfigLoader() {
            override fun resolvePackagedSidecarConfigPath() = configFile
        }.load()

        assertEquals(9393, config.port)
        assertEquals("/tmp/sidecar-apps", config.appsRoot)
    }

    @Test
    fun `fails when explicit external config does not exist`() {
        val missing = Files.createTempDirectory("ui-missing-config").resolve("missing.yml")

        val error = assertFailsWith<IllegalArgumentException> {
            object : UiConfigLoader() {
                override fun resolveExternalConfigPath() = missing
            }.load()
        }

        assertTrue(error.message!!.contains("UI-конфиг не найден"))
    }

    @Test
    fun `returns default ui config when classpath config is missing`() {
        val config = object : UiConfigLoader() {
            override fun resolveExternalConfigPath() = null
            override fun classpathConfigStream() = null
        }.load()

        assertEquals(UiAppConfig(), config)
    }

    @Test
    fun `resolves packaged sidecar from mac app bundle parent`() {
        val appDir = Files.createTempDirectory("ui-packaged-app")
        val launcherPath = appDir.resolve("LoadTestingDataPlatform.app/Contents/MacOS/LoadTestingDataPlatform")
        Files.createDirectories(launcherPath.parent)
        val sidecar = appDir.resolve("ui-application.yml").apply { writeText("ui:\n  port: 8182\n") }
        val previous = System.getProperty("jpackage.app-path")
        try {
            System.setProperty("jpackage.app-path", launcherPath.toString())

            val resolved = UiConfigLoader().resolvePackagedSidecarConfigPath()

            assertEquals(sidecar.toAbsolutePath().normalize(), resolved)
        } finally {
            if (previous != null) {
                System.setProperty("jpackage.app-path", previous)
            } else {
                System.clearProperty("jpackage.app-path")
            }
        }
    }

    @Test
    fun `packaged sidecar path is null when launcher path is unavailable`() {
        val loader = object : UiConfigLoader() {
            override fun resolveProcessCommand(): String? = null
        }
        val previous = System.getProperty("jpackage.app-path")
        try {
            System.clearProperty("jpackage.app-path")

            assertNull(loader.resolvePackagedSidecarConfigPath())
            assertNull(loader.resolvePackagedAppDirectory())
        } finally {
            if (previous != null) {
                System.setProperty("jpackage.app-path", previous)
            }
        }
    }

    @Test
    fun `configured apps root is resolved to absolute path`() {
        val appsRoot = Files.createTempDirectory("ui-apps-root")
        val path = UiAppConfig(
            appsRoot = appsRoot.toString(),
            sqlConsole = SqlConsoleConfig(),
        ).appsRootPath()

        assertNotNull(path)
        assertEquals(appsRoot.toAbsolutePath().normalize(), path)
    }

    @Test
    fun `apps root is null when not configured`() {
        assertNull(UiAppConfig(sqlConsole = SqlConsoleConfig()).appsRootPath())
    }

    @Test
    fun `configured storage dir is resolved to absolute path`() {
        val storageDir = Files.createTempDirectory("ui-storage-root")

        val path = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(),
        ).storageDirPath()

        assertEquals(storageDir.toAbsolutePath().normalize(), path)
    }

    @Test
    fun `relative storage dir is resolved against external config directory`() {
        val configDir = Files.createTempDirectory("ui-storage-config-dir")

        val path = UiAppConfig(
            storageDir = "storage",
            configBaseDir = configDir.toString(),
            sqlConsole = SqlConsoleConfig(),
        ).storageDirPath()

        assertEquals(configDir.resolve("storage").normalize(), path)
    }

    @Test
    fun `relative storage dir without config base resolves against current directory`() {
        val path = UiAppConfig(
            storageDir = "storage",
            sqlConsole = SqlConsoleConfig(),
        ).storageDirPath()

        assertEquals(java.nio.file.Path.of("storage").toAbsolutePath().normalize(), path)
    }

    @Test
    fun `default storage dir points to user home ui storage`() {
        val path = UiAppConfig(sqlConsole = SqlConsoleConfig()).storageDirPath()

        assertTrue(path.toString().contains(".datapool-loader"))
        assertTrue(path.toString().endsWith("ui/storage"))
    }

    @Test
    fun `default storage dir falls back to current directory when user home is blank`() {
        val previous = System.getProperty("user.home")
        try {
            System.setProperty("user.home", " ")

            val path = UiAppConfig(sqlConsole = SqlConsoleConfig()).storageDirPath()

            assertEquals(java.nio.file.Path.of(".datapool-loader/ui/storage").toAbsolutePath().normalize(), path)
        } finally {
            if (previous != null) {
                System.setProperty("user.home", previous)
            } else {
                System.clearProperty("user.home")
            }
        }
    }

    @Test
    fun `postgres config is configured only when jdbc url username and password are present`() {
        assertTrue(
            UiModuleStorePostgresConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                username = "registry_user",
                password = "registry_pwd",
            ).isConfigured()
        )
        assertTrue(!UiModuleStorePostgresConfig(jdbcUrl = "jdbc:postgresql://localhost:5432/modules").isConfigured())
        assertTrue(!UiModuleStorePostgresConfig(username = "registry_user", password = "registry_pwd").isConfigured())
    }

    @Test
    fun `blank postgres schema falls back to default schema name`() {
        val schemaName = UiModuleStorePostgresConfig(schema = "  ").schemaName()

        assertEquals(UiModuleStorePostgresConfig.DEFAULT_SCHEMA, schemaName)
    }
}

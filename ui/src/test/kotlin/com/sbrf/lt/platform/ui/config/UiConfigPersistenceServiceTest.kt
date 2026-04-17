package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiConfigPersistenceServiceTest {

    @Test
    fun `updates max rows in external config file`() {
        val configDir = Files.createTempDirectory("ui-persist-external")
        val configFile = configDir.resolve("ui-application.yml").apply {
            writeText(
                """
                ui:
                  port: 8080
                  appsRoot: /tmp/apps
                  sqlConsole:
                    maxRowsPerShard: 200
                    queryTimeoutSec: 60
                    sources:
                      - name: db1
                        jdbcUrl: jdbc:test
                        username: user
                        password: pwd
                """.trimIndent(),
            )
        }

        val service = UiConfigPersistenceService(
            uiConfigLoader = object : UiConfigLoader() {
                override fun resolveExternalConfigPath() = configFile
            },
        )

        val updated = service.updateSqlConsoleSettings(450, 120)

        assertEquals(450, updated.sqlConsole.maxRowsPerShard)
        assertEquals(120, updated.sqlConsole.queryTimeoutSec)
        assertTrue(configFile.readText().contains("maxRowsPerShard: 450"))
        assertTrue(configFile.readText().contains("queryTimeoutSec: 120"))
    }

    @Test
    fun `falls back to development ui resource config`() {
        val projectRoot = Files.createTempDirectory("ui-persist-dev")
        projectRoot.resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val configFile = projectRoot.resolve("ui/src/main/resources/application.yml").apply {
            parent.createDirectories()
            writeText(
                """
                ui:
                  port: 8080
                  sqlConsole:
                    maxRowsPerShard: 200
                """.trimIndent(),
            )
        }

        val service = UiConfigPersistenceService(
            uiConfigLoader = object : UiConfigLoader() {
                override fun resolveExternalConfigPath() = null
            },
            startDir = projectRoot.resolve("ui"),
        )

        val resolved = service.resolveEditableConfigPath()
        val updated = service.updateSqlConsoleSettings(320, null)

        assertNotNull(resolved)
        assertEquals(configFile.toAbsolutePath().normalize(), resolved)
        assertEquals(320, updated.sqlConsole.maxRowsPerShard)
        assertEquals(null, updated.sqlConsole.queryTimeoutSec)
        assertTrue(configFile.readText().contains("maxRowsPerShard: 320"))
    }

    @Test
    fun `updates module store mode in managed external config`() {
        val configDir = Files.createTempDirectory("ui-persist-mode")
        val configFile = configDir.resolve("ui-application.yml").apply {
            writeText(
                """
                ui:
                  port: 8080
                  moduleStore:
                    mode: files
                  sqlConsole:
                    maxRowsPerShard: 200
                """.trimIndent(),
            )
        }

        val service = object : UiConfigPersistenceService(
            uiConfigLoader = object : UiConfigLoader() {
                override fun resolveExternalConfigPath() = null
            },
        ) {
            override fun resolveManagedExternalConfigPath() = configFile
        }

        val updated = service.updateModuleStoreMode(UiModuleStoreMode.DATABASE)

        assertEquals(UiModuleStoreMode.DATABASE, updated.moduleStore.mode)
        assertTrue(configFile.readText().lowercase().contains("database"))
    }
}

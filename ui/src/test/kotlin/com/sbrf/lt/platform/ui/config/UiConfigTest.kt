package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UiConfigTest {

    @Test
    fun `loads ui config from classpath`() {
        val config = UiConfigLoader().load()

        assertEquals(8080, config.port)
        assertEquals(1000, config.sqlConsole.fetchSize)
        assertEquals(200, config.sqlConsole.maxRowsPerShard)
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
                      defaultCredentialsFile: /tmp/credentials.properties
                    """.trimIndent()
                )
            }

        val config = object : UiConfigLoader() {
            override fun resolveExternalConfigPath() = configFile
        }.load()

        assertEquals(9191, config.port)
        assertEquals("/tmp/apps-root", config.appsRoot)
        assertEquals("/tmp/credentials.properties", config.defaultCredentialsFile)
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
}

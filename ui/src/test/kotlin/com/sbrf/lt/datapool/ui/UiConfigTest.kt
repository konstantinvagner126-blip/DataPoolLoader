package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}

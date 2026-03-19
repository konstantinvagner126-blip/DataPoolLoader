package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.config.CredentialsFileLocator
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialsFileLocatorTest {

    @Test
    fun `returns explicit path first`() {
        val explicit = Files.createTempFile("credentials-explicit", ".properties")

        val resolved = CredentialsFileLocator.find(
            explicitPath = explicit.toString(),
            startDir = Files.createTempDirectory("credentials-start"),
        )

        assertEquals(explicit, resolved)
    }

    @Test
    fun `finds credential properties in nearest project gradle directory`() {
        val root = Files.createTempDirectory("credentials-root")
        val credentials = root.resolve("gradle").createDirectories().resolve("credential.properties")
        credentials.writeText("A=B")
        val nested = root.resolve("apps/demo/module").createDirectories()

        val resolved = CredentialsFileLocator.find(
            explicitPath = null,
            startDir = nested,
        )

        assertEquals(credentials, resolved)
    }

    @Test
    fun `returns null when nothing found and user home has no gradle credentials`() {
        val root = Files.createTempDirectory("credentials-empty")
        val fakeHome = Files.createTempDirectory("credentials-home")
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", fakeHome.toString())
            val resolved = CredentialsFileLocator.find(
                explicitPath = null,
                startDir = root,
            )
            assertNull(resolved)
        } finally {
            if (previousHome != null) {
                System.setProperty("user.home", previousHome)
            } else {
                System.clearProperty("user.home")
            }
        }
    }
}

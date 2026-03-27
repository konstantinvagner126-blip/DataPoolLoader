package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.config.ProjectRootLocator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectRootLocatorTest {

    @Test
    fun `finds project root by settings gradle`() {
        val root = Files.createTempDirectory("project-root")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"test\"")
        val nested = Files.createDirectories(root.resolve("apps/demo/src/main"))

        val found = ProjectRootLocator.find(nested)

        assertEquals(root, found)
    }

    @Test
    fun `returns null when settings gradle not found`() {
        val root = Files.createTempDirectory("project-root-missing")

        val found = ProjectRootLocator.find(root)

        assertNull(found)
    }
}

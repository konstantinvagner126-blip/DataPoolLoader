package com.sbrf.lt.datapool.ui

import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class ModuleRegistryTest {

    @Test
    fun `lists modules and loads sql labels`() {
        val root = createProject()
        val registry = ModuleRegistry(projectRoot = root)

        val modules = registry.listModules()
        assertEquals(listOf("demo-app"), modules.map { it.id })

        val details = registry.loadModuleDetails("demo-app")
        assertTrue(details.configText.contains("commonSqlFile"))
        assertEquals(
            listOf("Общий SQL", "Источник: db2"),
            details.sqlFiles.map { it.label },
        )
    }

    @Test
    fun `saves config and sql files back to module`() {
        val root = createProject()
        val registry = ModuleRegistry(projectRoot = root)

        registry.saveModule(
            "demo-app",
            SaveModuleRequest(
                configText = """
                    app:
                      commonSqlFile: classpath:sql/common.sql
                      sources:
                        - name: db2
                          sqlFile: classpath:sql/db2.sql
                """.trimIndent(),
                sqlFiles = mapOf(
                    "classpath:sql/common.sql" to "select 10",
                    "classpath:sql/db2.sql" to "select 20",
                ),
            ),
        )

        val resources = root.resolve("apps/demo-app/src/main/resources")
        assertEquals("select 10", resources.resolve("sql/common.sql").readText())
        assertEquals("select 20", resources.resolve("sql/db2.sql").readText())
    }

    @Test
    fun `resolves classpath and relative sql paths`() {
        val root = createProject()
        val registry = ModuleRegistry(projectRoot = root)
        val module = registry.getModule("demo-app")

        assertEquals(
            module.resourcesDir.resolve("sql/common.sql").normalize(),
            registry.resolveSqlPath(module, "classpath:sql/common.sql"),
        )
        assertEquals(
            module.configFile.parent.resolve("relative/query.sql").normalize(),
            registry.resolveSqlPath(module, "relative/query.sql"),
        )
    }

    private fun createProject() = Files.createTempDirectory("ui-modules").apply {
        resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val appDir = resolve("apps/demo-app")
        appDir.createDirectories()
        appDir.resolve("build.gradle.kts").writeText("plugins { application }")
        val resources = appDir.resolve("src/main/resources").createDirectories()
        resources.resolve("application.yml").writeText(
            """
            app:
              commonSqlFile: classpath:sql/common.sql
              sources:
                - name: db1
                - name: db2
                  sqlFile: classpath:sql/db2.sql
            """.trimIndent(),
        )
        resources.resolve("sql").createDirectories()
        resources.resolve("sql/common.sql").writeText("select 1")
        resources.resolve("sql/db2.sql").writeText("select 2")
    }
}

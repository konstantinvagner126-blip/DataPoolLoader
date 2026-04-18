package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.nio.file.Files

class ModuleRegistryTest {

    @Test
    fun `lists modules and loads sql labels`() {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))

        val modules = registry.listModules()
        assertEquals(listOf("demo-app"), modules.map { it.id })
        assertEquals("Demo App", modules.single().title)
        assertEquals("VALID", modules.single().validationStatus)
        assertTrue(modules.single().tags.contains("postgres"))

        val details = registry.loadModuleDetails("demo-app")
        assertTrue(details.configText.contains("commonSqlFile"))
        assertEquals(
            listOf("Общий SQL", "Источник: db2"),
            details.sqlFiles.map { it.label },
        )
        assertEquals("Demo App", details.title)
        assertEquals("Учебный модуль для UI-тестов.", details.description)
    }

    @Test
    fun `saves config and sql files back to module`() {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))

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
                title = "Demo App",
            ),
        )

        val resources = root.resolve("apps/demo-app/src/main/resources")
        assertEquals("select 10", resources.resolve("sql/common.sql").readText())
        assertEquals("select 20", resources.resolve("sql/db2.sql").readText())
    }

    @Test
    fun `resolves classpath and relative sql paths`() {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
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

    @Test
    fun `extracts unique sql references and skips blanks`() {
        val registry = ModuleRegistry()

        val refs = registry.extractSqlReferences(
            """
            app:
              commonSqlFile: classpath:sql/common.sql
              sources:
                - name: db1
                  sqlFile: classpath:sql/common.sql
                - name: db2
                  sqlFile: "   "
                - name: db3
                  sqlFile: relative/query.sql
            """.trimIndent(),
        )

        assertEquals(listOf("classpath:sql/common.sql", "relative/query.sql"), refs)
    }

    @Test
    fun `apps root status covers missing invalid and empty cases`() {
        val missingRoot = Files.createTempDirectory("ui-missing-root").resolve("apps")
        val missingStatus = ModuleRegistry(appsRoot = missingRoot).appsRootStatus()
        assertEquals("NOT_FOUND", missingStatus.mode)
        assertTrue(missingStatus.message.contains("не найден"))

        val fileRoot = Files.createTempFile("ui-apps-file", ".txt")
        val fileStatus = ModuleRegistry(appsRoot = fileRoot).appsRootStatus()
        assertEquals("NOT_DIRECTORY", fileStatus.mode)

        val emptyRoot = Files.createTempDirectory("ui-empty-apps")
        val emptyStatus = ModuleRegistry(appsRoot = emptyRoot).appsRootStatus()
        assertEquals("READY", emptyStatus.mode)
        assertTrue(emptyStatus.message.contains("не обнаружены"))
    }

    @Test
    fun `loads module details for missing sql file and get module fails for unknown id`() {
        val root = createProject()
        val resources = root.resolve("apps/demo-app/src/main/resources")
        resources.resolve("application.yml").writeText(
            """
            app:
              commonSqlFile: classpath:sql/missing.sql
              sources:
                - name: db1
                  sqlFile: relative/db1.sql
            """.trimIndent(),
        )
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))

        val details = registry.loadModuleDetails("demo-app")
        assertTrue(details.sqlFiles.any { it.path == "classpath:sql/missing.sql" && !it.exists && it.content.isEmpty() })
        assertTrue(details.sqlFiles.any { it.path == "relative/db1.sql" && !it.exists })
        assertTrue(details.sqlFiles.any { it.path == "classpath:sql/common.sql" && it.exists })
        assertEquals("INVALID", details.validationStatus)
        assertTrue(details.validationIssues.any { it.message.contains("missing.sql") })

        val error = assertFailsWith<IllegalStateException> {
            registry.getModule("unknown-module")
        }
        assertTrue(error.message!!.contains("не найден"))
    }

    @Test
    fun `list modules keeps modules without application config and marks them invalid`() {
        val root = createProject()
        val emptyModule = root.resolve("apps/empty-app")
        emptyModule.createDirectories()
        emptyModule.resolve("build.gradle.kts").writeText("plugins { application }")
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))

        val modules = registry.listModules()

        assertEquals(listOf("demo-app", "empty-app"), modules.map { it.id })
        val emptyApp = modules.first { it.id == "empty-app" }
        assertEquals("INVALID", emptyApp.validationStatus)
        assertTrue(emptyApp.validationIssues.any { it.message.contains("application.yml") })
        val details = registry.loadModuleDetails("empty-app")
        assertEquals("", details.configText)
    }

    private fun createProject() = Files.createTempDirectory("ui-modules").apply {
        resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val appDir = resolve("apps/demo-app")
        appDir.createDirectories()
        appDir.resolve("build.gradle.kts").writeText("plugins { application }")
        appDir.resolve("ui-module.yml").writeText(
            """
            title: Demo App
            description: Учебный модуль для UI-тестов.
            tags:
              - postgres
              - demo
            """.trimIndent(),
        )
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

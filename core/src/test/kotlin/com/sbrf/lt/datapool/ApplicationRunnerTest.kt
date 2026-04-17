package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.datapool.app.RunFinishedEvent
import com.sbrf.lt.datapool.app.RunStartedEvent
import com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.db.PostgresExporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.nio.file.Files
import java.sql.SQLException

class ApplicationRunnerTest {

    @Test
    fun `runs pipeline from runtime snapshot`() {
        val root = Files.createTempDirectory("runner-snapshot")
        val configFile = root.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              outputDir: ${root.resolve("out")}
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:test:db1
                  username: user
                  password: pwd
            """.trimIndent(),
        )

        val runner = ApplicationRunner(
            exporter = PostgresExporter { _, _, _ ->
                exportConnection(
                    columns = listOf("id", "name"),
                    rows = listOf(listOf(1, "A")),
                )
            },
        )
        val events = mutableListOf<ExecutionEvent>()

        val result = runner.run(
            snapshot = RuntimeModuleSnapshot(
                moduleCode = "db-demo",
                moduleTitle = "DB Demo",
                configYaml = Files.readString(configFile),
                sqlFiles = emptyMap(),
                appConfig = ConfigLoader().load(configFile),
                launchSourceKind = "DATABASE",
                executionSnapshotId = "snapshot-1",
                configLocation = "db:db-demo",
            ),
            credentialsPath = null,
            executionListener = ExecutionListener { events += it },
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals("db:db-demo", (events.first() as RunStartedEvent).configPath)
    }

    @Test
    fun `runs pipeline and skips schema mismatch source`() {
        val root = Files.createTempDirectory("runner-ok")
        val configFile = root.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              outputDir: ${root.resolve("out")}
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 2
              fetchSize: 100
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:test:db1
                  username: user
                  password: pwd
                - name: db2
                  jdbcUrl: jdbc:test:db2
                  username: user
                  password: pwd
            """.trimIndent(),
        )

        val exporter = PostgresExporter { url, _, _ ->
            when (url) {
                "jdbc:test:db1" -> exportConnection(
                    columns = listOf("id", "name"),
                    rows = listOf(listOf(1, "A"), listOf(2, "B")),
                )
                "jdbc:test:db2" -> exportConnection(
                    columns = listOf("id", "full_name"),
                    rows = listOf(listOf(3, "C")),
                )
                else -> error("Unknown url $url")
            }
        }
        val events = mutableListOf<ExecutionEvent>()
        val runner = ApplicationRunner(exporter = exporter)

        val result = runner.run(
            configPath = configFile,
            credentialsPath = null,
            executionListener = ExecutionListener { events += it },
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(2L, result.mergedRowCount)
        assertTrue(result.summaryFile!!.exists())
        val summaryJson = result.summaryFile!!.readText()
        assertTrue(summaryJson.contains("\"sourceName\" : \"db1\""))
        assertTrue(summaryJson.contains("\"sourceName\" : \"db2\""))
        assertTrue(events.any { it is SourceSchemaMismatchEvent })
        val finished = events.last() as RunFinishedEvent
        assertEquals(ExecutionStatus.SUCCESS, finished.status)
    }

    @Test
    fun `deletes generated csv files when cleanup enabled`() {
        val root = Files.createTempDirectory("runner-cleanup")
        val outputRoot = root.resolve("out")
        val configFile = root.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              outputDir: $outputRoot
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              deleteOutputFilesAfterCompletion: true
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:test:db1
                  username: user
                  password: pwd
            """.trimIndent(),
        )

        val runner = ApplicationRunner(
            exporter = PostgresExporter { _, _, _ ->
                exportConnection(
                    columns = listOf("id", "name"),
                    rows = listOf(listOf(1, "A")),
                )
            },
        )

        val result = runner.run(configPath = configFile, credentialsPath = null)

        assertFalse(result.outputDir.resolve("db1.csv").exists())
        assertFalse(result.outputDir.resolve("merged.csv").exists())
        assertTrue(result.summaryFile!!.exists())
    }

    @Test
    fun `fails when all sources fail`() {
        val root = Files.createTempDirectory("runner-fail")
        val configFile = root.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              outputDir: ${root.resolve("out")}
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:test:db1
                  username: user
                  password: pwd
            """.trimIndent(),
        )

        val events = mutableListOf<ExecutionEvent>()
        val runner = ApplicationRunner(
            exporter = PostgresExporter { _, _, _ -> throw SQLException("db down") },
        )

        val error = assertFailsWith<IllegalArgumentException> {
            runner.run(
                configPath = configFile,
                credentialsPath = null,
                executionListener = ExecutionListener { events += it },
            )
        }

        assertEquals("Все источники завершились ошибкой. Файл merged.csv не был создан.", error.message)
        assertIs<RunStartedEvent>(events.first())
        val finished = events.last() as RunFinishedEvent
        assertEquals(ExecutionStatus.FAILED, finished.status)
    }
}

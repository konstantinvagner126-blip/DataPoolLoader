package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.db.PostgresExporter
import com.sbrf.lt.datapool.db.PostgresImporter
import com.sbrf.lt.datapool.db.TargetTableValidator
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.ExportTask
import com.sbrf.lt.datapool.model.SourceConfig
import com.sbrf.lt.datapool.model.TargetConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("local-postgres")
class LocalPostgresIntegrationTest {

    @Test
    fun `exporter выгружает реальные данные из локального postgres`() {
        val schema = LocalPostgresTestSupport.createSchema("export")
        try {
            LocalPostgresTestSupport.connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        create table ${LocalPostgresTestSupport.quote(schema)}.source_data (
                            id bigint primary key,
                            payload text not null
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        insert into ${LocalPostgresTestSupport.quote(schema)}.source_data(id, payload)
                        values (1, 'A'), (2, 'B'), (3, 'C')
                        """.trimIndent(),
                    )
                }
            }

            val outputFile = Files.createTempFile("local-pg-export", ".csv")
            val exporter = PostgresExporter()
            val result = exporter.export(
                ExportTask(
                    source = SourceConfig(name = "local-db"),
                    resolvedJdbcUrl = LocalPostgresTestSupport.jdbcUrl,
                    resolvedUsername = LocalPostgresTestSupport.username,
                    resolvedPassword = LocalPostgresTestSupport.password,
                    sql = """
                        select id, payload
                        from ${LocalPostgresTestSupport.quote(schema)}.source_data
                        order by id
                    """.trimIndent(),
                    outputFile = outputFile,
                    fetchSize = 100,
                    progressLogEveryRows = 2,
                ),
            )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertEquals(3L, result.rowCount)
            assertEquals(listOf("id,payload", "1,A", "2,B", "3,C"), outputFile.readLines())
        } finally {
            LocalPostgresTestSupport.dropSchema(schema)
        }
    }

    @Test
    fun `validator и importer работают с локальным postgres`() {
        val schema = LocalPostgresTestSupport.createSchema("target")
        try {
            LocalPostgresTestSupport.connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        create table ${LocalPostgresTestSupport.quote(schema)}.target_data (
                            id bigint not null,
                            payload text not null
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        insert into ${LocalPostgresTestSupport.quote(schema)}.target_data(id, payload)
                        values (99, 'old')
                        """.trimIndent(),
                    )
                }
            }

            val validator = TargetTableValidator()
            validator.validate(
                target = TargetConfig(table = "$schema.target_data"),
                resolvedJdbcUrl = LocalPostgresTestSupport.jdbcUrl,
                resolvedUsername = LocalPostgresTestSupport.username,
                resolvedPassword = LocalPostgresTestSupport.password,
                incomingColumns = listOf("id", "payload"),
            )

            val mergedFile = Files.createTempFile("local-pg-merged", ".csv").apply {
                Files.writeString(this, "id,payload\n1,A\n2,B\n")
            }
            val importer = PostgresImporter()
            val result = importer.importCsv(
                target = TargetConfig(table = "$schema.target_data", truncateBeforeLoad = true),
                resolvedJdbcUrl = LocalPostgresTestSupport.jdbcUrl,
                resolvedUsername = LocalPostgresTestSupport.username,
                mergedFile = mergedFile,
                columns = listOf("id", "payload"),
                expectedRowCount = 2,
                resolvedPassword = LocalPostgresTestSupport.password,
            )

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            LocalPostgresTestSupport.connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        """
                        select count(*)
                        from ${LocalPostgresTestSupport.quote(schema)}.target_data
                        """.trimIndent(),
                    ).use { rs ->
                        rs.next()
                        assertEquals(2, rs.getInt(1))
                    }
                }
            }
        } finally {
            LocalPostgresTestSupport.dropSchema(schema)
        }
    }

    @Test
    fun `application runner проходит end to end на локальном postgres`() {
        val schema = LocalPostgresTestSupport.createSchema("runner")
        try {
            LocalPostgresTestSupport.connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        create table ${LocalPostgresTestSupport.quote(schema)}.source_data (
                            id bigint primary key,
                            payload text not null
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        create table ${LocalPostgresTestSupport.quote(schema)}.target_data (
                            id bigint not null,
                            payload text not null
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        insert into ${LocalPostgresTestSupport.quote(schema)}.source_data(id, payload)
                        values (1, 'A'), (2, 'B')
                        """.trimIndent(),
                    )
                }
            }

            val root = Files.createTempDirectory("local-pg-runner")
            val configFile = root.resolve("application.yml")
            Files.writeString(
                configFile,
                """
                app:
                  outputDir: ${root.resolve("output")}
                  fileFormat: csv
                  mergeMode: plain
                  errorMode: continue_on_error
                  parallelism: 1
                  fetchSize: 100
                  commonSql: |
                    select id, payload
                    from ${LocalPostgresTestSupport.quote(schema)}.source_data
                    order by id
                  target:
                    enabled: true
                    jdbcUrl: "${LocalPostgresTestSupport.jdbcUrl}"
                    username: "${LocalPostgresTestSupport.username}"
                    password: "${LocalPostgresTestSupport.password}"
                    table: $schema.target_data
                    truncateBeforeLoad: true
                  sources:
                    - name: db1
                      jdbcUrl: "${LocalPostgresTestSupport.jdbcUrl}"
                      username: "${LocalPostgresTestSupport.username}"
                      password: "${LocalPostgresTestSupport.password}"
                """.trimIndent(),
            )

            val result = ApplicationRunner().run(configPath = configFile, credentialsPath = null)

            assertEquals(ExecutionStatus.SUCCESS, result.status)
            assertEquals(2L, result.mergedRowCount)
            assertTrue(result.summaryFile!!.readText().contains("\"mergedRowCount\" : 2"))
            LocalPostgresTestSupport.connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        """
                        select count(*)
                        from ${LocalPostgresTestSupport.quote(schema)}.target_data
                        """.trimIndent(),
                    ).use { rs ->
                        rs.next()
                        assertEquals(2, rs.getInt(1))
                    }
                }
            }
        } finally {
            LocalPostgresTestSupport.dropSchema(schema)
        }
    }
}

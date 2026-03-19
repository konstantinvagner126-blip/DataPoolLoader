package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.model.MergeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ConfigLoaderTest {
    private val loader = ConfigLoader()

    @Test
    fun `loads source override sql`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              outputDir: ./output
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 2
              fetchSize: 500
              commonSql: select 1
              target:
                enabled: true
                jdbcUrl: jdbc:postgresql://localhost:5432/target_db
                username: loader
                password: target-secret
                table: public.test_data_pool
                truncateBeforeLoad: true
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
                  sql: select 2
            """.trimIndent()
        )

        val config = loader.load(file)

        assertEquals(MergeMode.PLAIN, config.mergeMode)
        assertEquals("select 2", config.sources.single().sql)
        assertEquals("public.test_data_pool", config.target.table)
        assertEquals(true, config.target.truncateBeforeLoad)
        assertEquals(10_000L, config.progressLogEveryRows)
    }

    @Test
    fun `rejects non select sql`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: delete from test
              target:
                enabled: true
                jdbcUrl: jdbc:postgresql://localhost:5432/target_db
                username: loader
                password: target-secret
                table: public.test_data_pool
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        assertFailsWith<IllegalArgumentException> {
            loader.load(file)
        }
    }

    @Test
    fun `rejects missing global sql`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: "   "
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val error = assertFailsWith<IllegalArgumentException> {
            loader.load(file)
        }

        assertEquals(
            "Для источника db1 не задан SQL-запрос. Укажите commonSql/commonSqlFile или source.sql/sqlFile.",
            error.message,
        )
    }

    @Test
    fun `allows disabled target without connection details`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
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
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(file)

        assertEquals(false, config.target.enabled)
    }

    @Test
    fun `loads quota configuration`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              fileFormat: csv
              mergeMode: quota
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: select 1
              target:
                enabled: false
              quotas:
                - source: db1
                  percent: 25
                - source: db2
                  percent: 75
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
                - name: db2
                  jdbcUrl: jdbc:postgresql://localhost:5432/db2
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(file)

        assertEquals(MergeMode.QUOTA, config.mergeMode)
        assertEquals(2, config.quotas.size)
    }

    @Test
    fun `loads progress and max merged rows`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              fileFormat: csv
              mergeMode: proportional
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              progressLogEveryRows: 2500
              maxMergedRows: 15000
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(file)

        assertEquals(2500L, config.progressLogEveryRows)
        assertEquals(15000L, config.maxMergedRows)
    }

    @Test
    fun `loads delete output files flag`() {
        val file = Files.createTempFile("config", ".yml")
        Files.writeString(
            file,
            """
            app:
              fileFormat: csv
              mergeMode: proportional
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              deleteOutputFilesAfterCompletion: true
              commonSql: select 1
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(file)

        assertEquals(true, config.deleteOutputFilesAfterCompletion)
    }

    @Test
    fun `loads common sql from file relative to config`() {
        val dir = Files.createTempDirectory("config-dir")
        val sqlDir = dir.resolve("sql").createDirectories()
        sqlDir.resolve("common.sql").writeText(
            """
            select id, created_at
            from common_source
            """.trimIndent()
        )
        val configFile = dir.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSqlFile: ./sql/common.sql
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(configFile)

        assertEquals(
            """
            select id, created_at
            from common_source
            """.trimIndent(),
            config.commonSql,
        )
        assertEquals("./sql/common.sql", config.commonSqlFile)
    }

    @Test
    fun `loads source override sql from file`() {
        val dir = Files.createTempDirectory("config-dir")
        dir.resolve("source.sql").writeText(
            """
            select id
            from source_override
            """.trimIndent()
        )
        val configFile = dir.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
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
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
                  sqlFile: ./source.sql
            """.trimIndent()
        )

        val config = loader.load(configFile)

        assertEquals(
            """
            select id
            from source_override
            """.trimIndent(),
            config.sources.single().sql,
        )
        assertEquals("./source.sql", config.sources.single().sqlFile)
    }

    @Test
    fun `loads common sql from classpath resource`() {
        val configFile = Files.createTempFile("config", ".yml")
        Files.writeString(
            configFile,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSqlFile: classpath:sql/classpath-common.sql
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val config = loader.load(configFile)

        assertEquals(
            """
            select id, created_at
            from classpath_common_source
            """.trimIndent(),
            config.commonSql,
        )
        assertEquals("classpath:sql/classpath-common.sql", config.commonSqlFile)
    }

    @Test
    fun `loads source override sql from classpath resource`() {
        val configFile = Files.createTempFile("config", ".yml")
        Files.writeString(
            configFile,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
                  sqlFile: classpath:sql/classpath-source.sql
            """.trimIndent()
        )

        val config = loader.load(configFile)

        assertEquals(
            """
            select id
            from classpath_source_override
            """.trimIndent(),
            config.sources.single().sql,
        )
        assertEquals("classpath:sql/classpath-source.sql", config.sources.single().sqlFile)
    }

    @Test
    fun `rejects common sql and common sql file together`() {
        val dir = Files.createTempDirectory("config-dir")
        dir.resolve("query.sql").writeText("select 1")
        val configFile = dir.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSql: select 1
              commonSqlFile: ./query.sql
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
            """.trimIndent()
        )

        val error = assertFailsWith<IllegalArgumentException> {
            loader.load(configFile)
        }

        assertEquals("Нельзя одновременно задавать commonSql и commonSqlFile.", error.message)
    }

    @Test
    fun `rejects source sql and source sql file together`() {
        val dir = Files.createTempDirectory("config-dir")
        dir.resolve("query.sql").writeText("select 1")
        val configFile = dir.resolve("application.yml")
        Files.writeString(
            configFile,
            """
            app:
              fileFormat: csv
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db1
                  username: user
                  password: secret
                  sql: select 1
                  sqlFile: ./query.sql
            """.trimIndent()
        )

        val error = assertFailsWith<IllegalArgumentException> {
            loader.load(configFile)
        }

        assertEquals("Для источника db1 нельзя одновременно задавать sql и sqlFile.", error.message)
    }
}

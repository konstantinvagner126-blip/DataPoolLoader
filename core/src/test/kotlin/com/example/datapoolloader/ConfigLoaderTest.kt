package com.example.datapoolloader

import com.example.datapoolloader.config.ConfigLoader
import com.example.datapoolloader.model.MergeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files

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

        assertEquals("Для источника db1 не задан SQL-запрос. Укажите commonSql или source.sql.", error.message)
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
}

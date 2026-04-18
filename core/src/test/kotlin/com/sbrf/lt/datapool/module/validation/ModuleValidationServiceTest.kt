package com.sbrf.lt.datapool.module.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleValidationServiceTest {
    private val service = ModuleValidationService()

    @Test
    fun `reports invalid numeric runtime parameters`() {
        val result = service.validate(
            """
            app:
              parallelism: 0
              fetchSize: -1
              queryTimeoutSec: 0
              progressLogEveryRows: 0
              maxMergedRows: 0
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost:5432/db
                  username: test
                  password: test
                  sql: select 1
            """.trimIndent(),
        )

        assertEquals(ModuleValidationStatus.INVALID, result.status)
        assertTrue(result.issues.any { it.message.contains("parallelism") })
        assertTrue(result.issues.any { it.message.contains("fetchSize") })
        assertTrue(result.issues.any { it.message.contains("queryTimeoutSec") })
        assertTrue(result.issues.any { it.message.contains("progressLogEveryRows") })
        assertTrue(result.issues.any { it.message.contains("maxMergedRows") })
    }

    @Test
    fun `reports source and target business rule violations`() {
        val result = service.validate(
            """
            app:
              sources:
                - name: ""
                  jdbcUrl: ""
                  username: ""
                  password: ""
              target:
                enabled: true
                jdbcUrl: ""
                username: ""
                password: ""
                table: ""
            """.trimIndent(),
        )

        assertEquals(ModuleValidationStatus.INVALID, result.status)
        assertTrue(result.issues.any { it.message.contains("Имя источника не должно быть пустым") })
        assertTrue(result.issues.any { it.message.contains("jdbcUrl источника") })
        assertTrue(result.issues.any { it.message.contains("Имя пользователя источника") })
        assertTrue(result.issues.any { it.message.contains("Пароль источника") })
        assertTrue(result.issues.any { it.message.contains("не задан SQL-запрос") })
        assertTrue(result.issues.any { it.message.contains("jdbcUrl для целевой БД") })
        assertTrue(result.issues.any { it.message.contains("Имя пользователя для целевой БД") })
        assertTrue(result.issues.any { it.message.contains("Пароль для целевой БД") })
        assertTrue(result.issues.any { it.message.contains("Имя целевой таблицы") })
    }
}

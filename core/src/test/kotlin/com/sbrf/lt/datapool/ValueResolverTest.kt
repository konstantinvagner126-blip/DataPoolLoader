package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.config.ValueResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.file.Files

class ValueResolverTest {
    @Test
    fun `resolves value from credentials file`() {
        val file = Files.createTempFile("credential", ".properties")
        Files.writeString(
            file,
            """
            DB1_JDBC_URL=jdbc:postgresql://localhost:5432/db1
            DB1_USERNAME=user1
            DB1_PASSWORD=secret
            """.trimIndent()
        )

        val resolver = ValueResolver.fromFile(file)

        assertEquals("jdbc:postgresql://localhost:5432/db1", resolver.resolve("\${DB1_JDBC_URL}"))
        assertEquals("user1", resolver.resolve("\${DB1_USERNAME}"))
        assertEquals("plain", resolver.resolve("plain"))
    }

    @Test
    fun `fails for unknown placeholder`() {
        val resolver = ValueResolver.fromFile(null)

        assertFailsWith<IllegalArgumentException> {
            resolver.resolve("\${UNKNOWN_KEY}")
        }
    }

    @Test
    fun `resolves value from credentials file with utf8 bom`() {
        val file = Files.createTempFile("credential-bom", ".properties")
        Files.writeString(
            file,
            "\uFEFFDB1_JDBC_URL=jdbc:postgresql://localhost:5432/db1\nDB1_USERNAME=user1\n",
        )

        val resolver = ValueResolver.fromFile(file)

        assertEquals("jdbc:postgresql://localhost:5432/db1", resolver.resolve("\${DB1_JDBC_URL}"))
        assertEquals("user1", resolver.resolve("\${DB1_USERNAME}"))
    }
}

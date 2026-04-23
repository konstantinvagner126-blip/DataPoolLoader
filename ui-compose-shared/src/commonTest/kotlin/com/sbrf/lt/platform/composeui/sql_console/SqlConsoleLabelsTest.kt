package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleLabelsTest {
    @Test
    fun `buildCredentialsStatusBadgeText prefers uploaded credentials`() {
        val status = CredentialsStatusResponse(
            mode = "FILE",
            displayName = "credential.properties",
            fileAvailable = true,
            uploaded = true,
        )

        assertEquals("загружен через UI", buildCredentialsStatusBadgeText(status))
    }

    @Test
    fun `buildCredentialsStatusBadgeText distinguishes missing default file`() {
        val status = CredentialsStatusResponse(
            mode = "FILE",
            displayName = "credential.properties",
            fileAvailable = false,
            uploaded = false,
        )

        assertEquals("файл не найден", buildCredentialsStatusBadgeText(status))
    }

    @Test
    fun `translateTransactionMode renders known modes`() {
        assertEquals("Autocommit", translateTransactionMode("AUTO_COMMIT"))
        assertEquals("Транзакция по source", translateTransactionMode("TRANSACTION_PER_SHARD"))
    }
}

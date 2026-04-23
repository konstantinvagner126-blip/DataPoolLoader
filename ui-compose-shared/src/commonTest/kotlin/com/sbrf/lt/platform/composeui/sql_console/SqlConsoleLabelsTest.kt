package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleLabelsTest {
    private val info = SqlConsoleInfo(
        configured = true,
        sourceCatalog = listOf(
            SqlConsoleSourceCatalogEntry("db1"),
            SqlConsoleSourceCatalogEntry("db2"),
            SqlConsoleSourceCatalogEntry("db3"),
            SqlConsoleSourceCatalogEntry("db4"),
            SqlConsoleSourceCatalogEntry("db5"),
        ),
        groups = listOf(
            SqlConsoleSourceGroup(name = "dev", sources = listOf("db1", "db2")),
            SqlConsoleSourceGroup(name = "ift", sources = listOf("db2", "db3")),
            SqlConsoleSourceGroup(name = "lt", sources = listOf("db4")),
        ),
        maxRowsPerShard = 100,
    )

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

    @Test
    fun `buildSelectedGroupsSummary distinguishes manual selection`() {
        assertEquals(
            "без групп",
            buildSelectedGroupsSummary(
                info = info,
                selectedGroupNames = emptyList(),
                selectedSourceNames = listOf("db2"),
            ),
        )
        assertEquals(
            "dev, ift +1",
            buildSelectedGroupsSummary(
                info = info,
                selectedGroupNames = listOf("dev", "ift", "lt"),
                selectedSourceNames = listOf("db1", "db2", "db3", "db4"),
            ),
        )
    }

    @Test
    fun `buildSelectedSourcesSummary uses compact count for larger selection`() {
        assertEquals("db1, db2", buildSelectedSourcesSummary(listOf("db1", "db2")))
        assertEquals("3 источника", buildSelectedSourcesSummary(listOf("db1", "db2", "db3")))
        assertEquals("5 источников", buildSelectedSourcesSummary(listOf("db1", "db2", "db3", "db4", "db5")))
    }

    @Test
    fun `buildSelectedContextPills separates groups manual sources ungrouped and exclusions`() {
        assertEquals(
            listOf(
                SqlConsoleContextSelectionPill(label = "Группа", value = "dev", tone = "primary"),
                SqlConsoleContextSelectionPill(label = "Вручную", value = "db3", tone = "neutral"),
                SqlConsoleContextSelectionPill(label = "Без группы", value = "db5", tone = "neutral"),
                SqlConsoleContextSelectionPill(label = "Исключено", value = "db2", tone = "warning"),
            ),
            buildSelectedContextPills(
                info = info,
                selectedGroupNames = listOf("dev"),
                manuallyIncludedSourceNames = listOf("db3", "db5"),
                manuallyExcludedSourceNames = listOf("db2"),
            ),
        )
    }
}

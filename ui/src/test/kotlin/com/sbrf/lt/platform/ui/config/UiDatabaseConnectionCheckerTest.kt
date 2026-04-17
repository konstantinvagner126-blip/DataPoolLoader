package com.sbrf.lt.platform.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiDatabaseConnectionCheckerTest {

    @Test
    fun `reports unresolved placeholders before jdbc probe`() {
        var probeInvoked = false
        val checker = UiDatabaseConnectionChecker(
            probe = {
                probeInvoked = true
            },
        )

        val status = checker.check(
            UiModuleStorePostgresConfig(
                jdbcUrl = "\${LOCAL_MANUAL_DB_JDBC_URL}",
                username = "\${LOCAL_MANUAL_DB_USERNAME}",
                password = "\${LOCAL_MANUAL_DB_PASSWORD}",
            ),
        )

        assertTrue(status.configured)
        assertFalse(status.available)
        assertEquals(
            "Не удалось разрешить placeholders для полей: jdbcUrl, username, password.",
            status.errorMessage,
        )
        assertFalse(probeInvoked)
    }
}

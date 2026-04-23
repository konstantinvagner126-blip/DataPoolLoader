package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlConsoleOwnershipRecoverySupportTest {
    @Test
    fun `restores ownership only for same session and same tab instance`() {
        assertTrue(
            canRestoreSqlConsoleExecutionOwnership(
                storedOwnerSessionId = "session-1",
                storedOwnerTabInstanceId = "tab-1",
                currentOwnerSessionId = "session-1",
                currentOwnerTabInstanceId = "tab-1",
            ),
        )
        assertFalse(
            canRestoreSqlConsoleExecutionOwnership(
                storedOwnerSessionId = "session-1",
                storedOwnerTabInstanceId = "tab-1",
                currentOwnerSessionId = "session-1",
                currentOwnerTabInstanceId = "tab-2",
            ),
        )
        assertFalse(
            canRestoreSqlConsoleExecutionOwnership(
                storedOwnerSessionId = "session-1",
                storedOwnerTabInstanceId = "tab-1",
                currentOwnerSessionId = "session-2",
                currentOwnerTabInstanceId = "tab-1",
            ),
        )
    }

    @Test
    fun `does not restore legacy owner state without tab discriminator`() {
        assertFalse(
            canRestoreSqlConsoleExecutionOwnership(
                storedOwnerSessionId = "session-1",
                storedOwnerTabInstanceId = null,
                currentOwnerSessionId = "session-1",
                currentOwnerTabInstanceId = "tab-1",
            ),
        )
    }
}

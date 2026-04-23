package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleEditorStoreConfigFormSnapshotSupportTest {

    @Test
    fun `config form snapshot keeps parsed state on success`() {
        val expected = ConfigFormStateDto(
            outputDir = "/tmp/out",
            fileFormat = "csv",
            mergeMode = "APPEND",
            errorMode = "FAIL_FAST",
            parallelism = 2,
            fetchSize = 100,
            queryTimeoutSec = 30,
            progressLogEveryRows = 500,
            maxMergedRows = 1_000,
            deleteOutputFilesAfterCompletion = false,
            commonSql = "select 1",
            targetEnabled = false,
            targetJdbcUrl = "",
            targetUsername = "",
            targetPassword = "",
            targetTable = "",
            targetTruncateBeforeLoad = false,
        )
        val support = ModuleEditorStoreConfigFormSnapshotSupport(
            StubModuleEditorApi(
                parseConfigFormHandler = { expected },
            ),
        )

        val snapshot = runModuleEditorSuspend { support.loadSnapshot("demo") }

        assertEquals(expected, snapshot.state)
        assertNull(snapshot.errorMessage)
    }

    @Test
    fun `config form snapshot converts parse error into user-facing message`() {
        val support = ModuleEditorStoreConfigFormSnapshotSupport(
            StubModuleEditorApi(
                parseConfigFormHandler = { error("broken form") },
            ),
        )

        val snapshot = runModuleEditorSuspend { support.loadSnapshot("demo") }

        assertNull(snapshot.state)
        assertEquals("broken form", snapshot.errorMessage)
    }
}

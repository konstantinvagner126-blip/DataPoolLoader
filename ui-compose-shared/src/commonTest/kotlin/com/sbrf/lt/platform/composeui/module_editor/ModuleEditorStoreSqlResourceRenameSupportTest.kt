package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSqlResourceRenameSupportTest {
    private val namingSupport = ModuleEditorStoreSqlResourceNamingSupport()

    @Test
    fun `renameSqlResource renames selected file and delegates form sync`() {
        var capturedCurrentPath: String? = null
        var capturedNextPath: String? = null
        val support = ModuleEditorStoreSqlResourceRenameSupport(
            formSyncStore = StubModuleEditorSqlResourceFormSyncStore(
                renameHandler = { current, currentPath, nextPath ->
                    capturedCurrentPath = currentPath
                    capturedNextPath = nextPath
                    current.copy(configTextDraft = "updated-by-form-sync")
                },
            ),
            namingSupport = namingSupport,
        )

        val state = runModuleEditorSuspend {
            support.renameSqlResource(
                current = ModuleEditorPageState(
                    selectedSqlPath = "classpath:sql/main.sql",
                    sqlContentsDraft = linkedMapOf(
                        "classpath:sql/main.sql" to "select 1",
                        "classpath:sql/secondary.sql" to "select 2",
                    ),
                ),
                rawName = "Renamed Main",
            )
        }

        assertEquals("classpath:sql/main.sql", capturedCurrentPath)
        assertEquals("classpath:sql/renamed-main.sql", capturedNextPath)
        assertEquals("classpath:sql/renamed-main.sql", state.selectedSqlPath)
        assertEquals(
            listOf("classpath:sql/renamed-main.sql", "classpath:sql/secondary.sql"),
            state.sqlContentsDraft.keys.toList(),
        )
        assertEquals("updated-by-form-sync", state.configTextDraft)
        assertEquals(
            "SQL-ресурс переименован: 'classpath:sql/main.sql' -> 'classpath:sql/renamed-main.sql'.",
            state.successMessage,
        )
    }

    @Test
    fun `renameSqlResource rejects duplicate target path`() {
        val support = ModuleEditorStoreSqlResourceRenameSupport(
            formSyncStore = StubModuleEditorSqlResourceFormSyncStore(),
            namingSupport = namingSupport,
        )

        val state = runModuleEditorSuspend {
            support.renameSqlResource(
                current = ModuleEditorPageState(
                    selectedSqlPath = "classpath:sql/main.sql",
                    sqlContentsDraft = linkedMapOf(
                        "classpath:sql/main.sql" to "select 1",
                        "classpath:sql/report.sql" to "select 2",
                    ),
                ),
                rawName = "report.sql",
            )
        }

        assertEquals("SQL-ресурс 'classpath:sql/report.sql' уже существует.", state.errorMessage)
    }
}

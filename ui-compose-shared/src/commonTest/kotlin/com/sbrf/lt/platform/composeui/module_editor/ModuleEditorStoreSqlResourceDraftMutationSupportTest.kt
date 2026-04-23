package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSqlResourceDraftMutationSupportTest {
    private val namingSupport = ModuleEditorStoreSqlResourceNamingSupport()

    @Test
    fun `createSqlResource adds normalized path and selects it`() {
        val support = ModuleEditorStoreSqlResourceDraftMutationSupport(
            formSyncStore = StubModuleEditorSqlResourceFormSyncStore(),
            namingSupport = namingSupport,
        )

        val state = support.createSqlResource(
            current = ModuleEditorPageState(
                sqlContentsDraft = linkedMapOf("classpath:sql/a.sql" to "select 1"),
            ),
            rawName = "Report Main",
        )

        assertEquals("classpath:sql/report-main.sql", state.selectedSqlPath)
        assertEquals(
            listOf("classpath:sql/a.sql", "classpath:sql/report-main.sql"),
            state.sqlContentsDraft.keys.toList(),
        )
        assertEquals(
            "Создан SQL-ресурс 'classpath:sql/report-main.sql'. Сохрани модуль, чтобы зафиксировать изменения.",
            state.successMessage,
        )
    }

    @Test
    fun `deleteSqlResource blocks deletion while resource is used by config form`() {
        val support = ModuleEditorStoreSqlResourceDraftMutationSupport(
            formSyncStore = StubModuleEditorSqlResourceFormSyncStore(
                usagesHandler = { _, _ -> listOf("SQL по умолчанию", "Источник: alpha") },
            ),
            namingSupport = namingSupport,
        )

        val state = support.deleteSqlResource(
            ModuleEditorPageState(
                selectedSqlPath = "classpath:sql/main.sql",
                sqlContentsDraft = linkedMapOf("classpath:sql/main.sql" to "select 1"),
            ),
        )

        assertEquals(
            "Нельзя удалить SQL-ресурс, пока он используется: SQL по умолчанию, Источник: alpha.",
            state.errorMessage,
        )
        assertEquals(listOf("classpath:sql/main.sql"), state.sqlContentsDraft.keys.toList())
    }
}

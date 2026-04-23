package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStorageRunSupportTest {

    @Test
    fun `files storage delegates run to files api with current draft request`() {
        var capturedRequest: StartRunRequestDto? = null
        val support = ModuleEditorStorageRunSupport(
            api = StubModuleEditorApi(
                startFilesRunHandler = { request ->
                    capturedRequest = request
                    UiRunSnapshotDto(
                        id = "run-1",
                        moduleId = request.moduleId,
                        moduleTitle = "Demo",
                        status = "RUNNING",
                        startedAt = "2026-04-23T10:00:00Z",
                    )
                },
            ),
        )

        runModuleEditorSuspend {
            support.run(
                storage = "files",
                moduleId = "module-a",
                current = ModuleEditorPageState(
                    configTextDraft = "app: {}",
                    sqlContentsDraft = linkedMapOf("classpath:sql/main.sql" to "select 1"),
                ),
            )
        }

        assertEquals("module-a", capturedRequest?.moduleId)
        assertEquals("app: {}", capturedRequest?.configText)
        assertEquals(
            linkedMapOf("classpath:sql/main.sql" to "select 1"),
            capturedRequest?.sqlFiles,
        )
    }

    @Test
    fun `database storage delegates run to database api`() {
        var capturedModuleId: String? = null
        val support = ModuleEditorStorageRunSupport(
            api = StubModuleEditorApi(
                startDatabaseRunHandler = { moduleId ->
                    capturedModuleId = moduleId
                    DatabaseRunStartResponseDto(
                        runId = "run-1",
                        moduleCode = "demo",
                        status = "RUNNING",
                        requestedAt = "2026-04-23T10:00:00Z",
                        launchSourceKind = "MODULE_EDITOR",
                        executionSnapshotId = "snapshot-1",
                        message = "started",
                    )
                },
            ),
        )

        runModuleEditorSuspend {
            support.run(
                storage = "database",
                moduleId = "module-b",
                current = ModuleEditorPageState(),
            )
        }

        assertEquals("module-b", capturedModuleId)
    }
}

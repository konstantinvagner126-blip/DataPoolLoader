package com.sbrf.lt.platform.composeui.module_sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleSyncStoreActionSupportTest {

    @Test
    fun `syncOne validates that exactly one module is selected`() {
        val support = ModuleSyncStoreActionSupport(
            api = StubModuleSyncApi(),
            loadStore = StubModuleSyncLoadStore { _, _, _, _, _ ->
                error("load should not be called")
            },
        )

        val state = runModuleSyncSuspend {
            support.syncOne(
                ModuleSyncPageState(
                    actionInProgress = "syncOne",
                    selectedModuleCodes = linkedSetOf("alpha", "beta"),
                ),
            )
        }

        assertEquals(null, state.actionInProgress)
        assertEquals("Выбери один модуль для точечной синхронизации.", state.errorMessage)
    }

    @Test
    fun `syncSelected reloads state with normalized module codes and success message`() {
        var capturedModuleCodes: List<String>? = null
        var capturedSelectedCodes: Set<String>? = null
        var capturedPreferredRunId: String? = null
        val support = ModuleSyncStoreActionSupport(
            api = StubModuleSyncApi(
                syncSelectedHandler = { moduleCodes ->
                    capturedModuleCodes = moduleCodes
                    SyncRunResultResponse(
                        syncRunId = "run-42",
                        scope = "SELECTED",
                        status = "RUNNING",
                        startedAt = "2026-04-23T10:00:00Z",
                    )
                },
            ),
            loadStore = StubModuleSyncLoadStore { historyLimit, preferredRunId, selectiveSyncVisible, selectedModuleCodes, moduleSearchQuery ->
                capturedPreferredRunId = preferredRunId
                capturedSelectedCodes = selectedModuleCodes
                ModuleSyncPageState(
                    loading = false,
                    historyLimit = historyLimit,
                    selectiveSyncVisible = selectiveSyncVisible,
                    selectedModuleCodes = selectedModuleCodes,
                    moduleSearchQuery = moduleSearchQuery,
                )
            },
        )

        val state = runModuleSyncSuspend {
            support.syncSelected(
                ModuleSyncPageState(
                    historyLimit = 30,
                    actionInProgress = "syncSelected",
                    moduleSearchQuery = "demo",
                    selectedModuleCodes = linkedSetOf(" alpha ", "beta", "alpha"),
                ),
            )
        }

        assertEquals(listOf("alpha", "beta"), capturedModuleCodes)
        assertEquals("run-42", capturedPreferredRunId)
        assertEquals(linkedSetOf("alpha", "beta"), capturedSelectedCodes)
        assertEquals(false, state.selectiveSyncVisible)
        assertEquals("Выборочная синхронизация запущена для 2 модулей.", state.successMessage)
        assertEquals(null, state.actionInProgress)
    }
}

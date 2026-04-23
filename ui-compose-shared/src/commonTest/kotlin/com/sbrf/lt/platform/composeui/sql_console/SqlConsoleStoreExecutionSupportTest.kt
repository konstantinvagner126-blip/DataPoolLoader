package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlConsoleStoreExecutionSupportTest {

    @Test
    fun `strict safety blocks mutating query before api call`() {
        var startCalls = 0
        val store = SqlConsoleStore(
            StubSqlConsoleApi(
                startQueryHandler = {
                    startCalls += 1
                    error("startQuery should not be called")
                },
            ),
        )

        val next = runSuspend {
            store.startQuery(
                current = SqlConsolePageState(
                    info = sampleSqlConsoleInfo(),
                    draftSql = "delete from public.offer where id = 1",
                    selectedSourceNames = listOf("db1"),
                    strictSafetyEnabled = true,
                ),
                workspaceId = "workspace-a",
                ownerSessionId = "tab-1",
            )
        }

        assertEquals(0, startCalls)
        assertEquals(
            "Строгая защита включена. Для выполнения изменяющего запроса сначала отключи этот режим.",
            next.errorMessage,
        )
        assertNull(next.currentExecutionId)
        assertNull(next.successMessage)
    }

    @Test
    fun `strict safety blocks explain analyze for mutating query before api call`() {
        var startCalls = 0
        val store = SqlConsoleStore(
            StubSqlConsoleApi(
                startQueryHandler = {
                    startCalls += 1
                    error("startQuery should not be called")
                },
            ),
        )

        val next = runSuspend {
            store.startQuery(
                current = SqlConsolePageState(
                    info = sampleSqlConsoleInfo(),
                    draftSql = "explain analyze update public.offer set name = 'x'",
                    selectedSourceNames = listOf("db1"),
                    strictSafetyEnabled = true,
                ),
                workspaceId = "workspace-a",
                ownerSessionId = "tab-1",
            )
        }

        assertEquals(0, startCalls)
        assertEquals(
            "Строгая защита включена. Для выполнения изменяющего запроса сначала отключи этот режим.",
            next.errorMessage,
        )
        assertNull(next.currentExecutionId)
        assertNull(next.successMessage)
    }

    @Test
    fun `strict safety still allows plan only explain for mutating query`() {
        var startCalls = 0
        val store = SqlConsoleStore(
            StubSqlConsoleApi(
                startQueryHandler = {
                    startCalls += 1
                    SqlConsoleStartQueryResponse(
                        id = "exec-explain",
                        status = "RUNNING",
                        startedAt = "2026-04-23T11:00:00Z",
                        cancelRequested = false,
                        autoCommitEnabled = true,
                        transactionState = "NONE",
                        ownerToken = "token-1",
                    )
                },
            ),
        )

        val next = runSuspend {
            store.startQuery(
                current = SqlConsolePageState(
                    info = sampleSqlConsoleInfo(),
                    draftSql = "explain update public.offer set name = 'x'",
                    selectedSourceNames = listOf("db1"),
                    strictSafetyEnabled = true,
                ),
                workspaceId = "workspace-a",
                ownerSessionId = "tab-1",
                successMessage = "EXPLAIN запущен.",
            )
        }

        assertEquals(1, startCalls)
        assertEquals("exec-explain", next.currentExecutionId)
        assertEquals("EXPLAIN запущен.", next.successMessage)
        assertNull(next.errorMessage)
    }

    @Test
    fun `heartbeat ownership loss clears owner token but keeps execution snapshot`() {
        val store = SqlConsoleStore(
            StubSqlConsoleApi(
                heartbeatExecutionHandler = { _, _ ->
                    throw IllegalStateException("Execution session больше не принадлежит этой вкладке.")
                },
            ),
        )

        val current = SqlConsolePageState(
            currentExecutionId = "exec-1",
            currentExecution = SqlConsoleExecutionResponse(
                id = "exec-1",
                status = "RUNNING",
                startedAt = "2026-04-23T10:00:00Z",
                cancelRequested = false,
                ownerToken = "token-1",
            ),
        )

        val next = runSuspend { store.heartbeatExecution(current, ownerSessionId = "tab-1") }

        assertEquals("exec-1", next.currentExecutionId)
        assertEquals("RUNNING", next.currentExecution?.status)
        assertNull(next.currentExecution?.ownerToken)
        assertEquals("Execution session больше не принадлежит этой вкладке.", next.errorMessage)
        assertNull(next.successMessage)
    }

    @Test
    fun `commit requires current tab ownership`() {
        val store = SqlConsoleStore(StubSqlConsoleApi())
        val current = SqlConsolePageState(
            currentExecutionId = "exec-2",
            currentExecution = SqlConsoleExecutionResponse(
                id = "exec-2",
                status = "SUCCESS",
                startedAt = "2026-04-23T10:05:00Z",
                cancelRequested = false,
                transactionState = "PENDING_COMMIT",
                ownerToken = null,
            ),
        )

        val next = runSuspend { store.commitExecution(current, ownerSessionId = "tab-1") }

        assertEquals("Execution session больше не принадлежит этой вкладке.", next.errorMessage)
        assertNull(next.successMessage)
        assertEquals("exec-2", next.currentExecutionId)
    }
}

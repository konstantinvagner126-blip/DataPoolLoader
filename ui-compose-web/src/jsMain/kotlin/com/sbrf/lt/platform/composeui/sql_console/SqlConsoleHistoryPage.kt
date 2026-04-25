package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div

@Composable
fun ComposeSqlConsoleHistoryPage(
    initialParams: Map<String, String> = emptyMap(),
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val workspaceId = initialParams["workspaceId"]?.trim().orEmpty()
        .ifBlank { resolveSqlConsoleWorkspaceId() }
    val ownerSessionId = remember { resolveSqlConsoleOwnerSessionId() }
    val ownerTabInstanceId = remember { resolveSqlConsoleOwnerTabInstanceId() }
    val store = remember(api) { SqlConsoleStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SqlConsolePageState()) }

    LaunchedEffect(store, workspaceId) {
        state = store.startLoading(state)
        state = store.load(workspaceId)
    }

    fun navigateBackToConsole() {
        window.location.href = buildSqlConsoleWorkspaceHref(workspaceId)
    }

    fun applyExecutionHistory(entry: SqlConsoleExecutionHistoryEntry) {
        scope.launch {
            val preparedState = store.applyExecutionHistoryEntry(state, entry)
            state = store.persistState(preparedState, workspaceId)
            navigateBackToConsole()
        }
    }

    fun repeatExecutionHistory(entry: SqlConsoleExecutionHistoryEntry) {
        scope.launch {
            val preparedState = store.applyExecutionHistoryEntry(state, entry)
            val persistedState = store.persistState(preparedState, workspaceId)
            if (persistedState.selectedSourceNames.isEmpty()) {
                state = persistedState
                return@launch
            }
            val startedState = store.startQuery(
                current = persistedState,
                workspaceId = workspaceId,
                ownerSessionId = ownerSessionId,
                sqlOverride = entry.sql,
                successMessage = "Запрос из истории запусков запущен.",
            )
            state = startedState
            startedState.currentExecutionId?.let { executionId ->
                startedState.currentExecution?.ownerToken?.let { ownerToken ->
                    saveSqlConsoleExecutionOwnerState(
                        SqlConsoleExecutionOwnerState(
                            executionId = executionId,
                            ownerSessionId = ownerSessionId,
                            ownerTabInstanceId = ownerTabInstanceId,
                            ownerToken = ownerToken,
                        ),
                    )
                }
            }
            if (startedState.errorMessage == null) {
                navigateBackToConsole()
            }
        }
    }

    PageScaffold(
        eyebrow = "MLP Platform",
        title = "История запусков SQL-консоли",
        subtitle = "Отдельный журнал текущего workspace: что запускалось, по каким источникам и с каким итогом.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                SqlConsoleNavActionButton("На главную", hrefValue = "/")
                SqlConsoleNavActionButton(
                    "SQL-консоль",
                    hrefValue = buildSqlConsoleWorkspaceHref(workspaceId),
                )
                SqlConsoleNavActionButton("История запусков", active = true)
                SqlConsoleNavActionButton("Источники", hrefValue = "/static/compose-app/index.html?screen=sql-console-sources")
            }
        },
        content = {
            SqlConsoleHistoryPageContent(
                state = state,
                workspaceId = workspaceId,
                onApplyExecutionHistory = ::applyExecutionHistory,
                onRepeatExecutionHistory = ::repeatExecutionHistory,
            )
        },
    )
}

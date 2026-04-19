package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeRunHistoryCleanupPage(
    api: RunHistoryCleanupApi = remember { RunHistoryCleanupApiClient() },
) {
    val store = remember(api) { RunHistoryCleanupStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(RunHistoryCleanupPageState()) }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = store.load()
    }

    val runtimeContext = state.runtimeContext
    val storageMode = runtimeContext?.requestedMode

    PageScaffold(
        eyebrow = "Нагрузочное тестирование",
        title = "Обслуживание запусков",
        subtitle = buildSubtitle(storageMode),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("Очистка истории") }
            }
        },
        content = {
            runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
                AlertBanner(
                    buildRuntimeModeFallbackMessage(
                        fallbackContext,
                        suffix = "Экран показывает состояние выбранного режима, а операции для БД будут недоступны до восстановления подключения.",
                    ),
                    "warning",
                )
            }
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }

            CleanupSection(
                state = state,
                onToggleDisableSafeguard = { disableSafeguard ->
                    scope.launch {
                        state = store.updateCleanupSafeguard(state, disableSafeguard)
                        state = store.beginAction(state, "cleanup-preview")
                        state = store.refreshPreview(state)
                    }
                },
                onRefreshPreview = {
                    scope.launch {
                        state = store.beginAction(state, "cleanup-preview")
                        state = store.refreshPreview(state)
                    }
                },
                onExecuteCleanup = {
                    if (!window.confirm("Очистить историю запусков по текущему preview?")) {
                        return@CleanupSection
                    }
                    scope.launch {
                        state = store.beginAction(state, "cleanup-execute")
                        state = store.cleanupRunHistory(state)
                    }
                },
            )

            OutputRetentionSection(
                state = state,
                onToggleDisableSafeguard = { disableSafeguard ->
                    scope.launch {
                        state = store.updateOutputSafeguard(state, disableSafeguard)
                        state = store.beginAction(state, "output-preview")
                        state = store.refreshOutputPreview(state)
                    }
                },
                onRefreshPreview = {
                    scope.launch {
                        state = store.beginAction(state, "output-preview")
                        state = store.refreshOutputPreview(state)
                    }
                },
                onExecuteCleanup = {
                    if (!window.confirm("Очистить output-каталоги по текущему preview?")) {
                        return@OutputRetentionSection
                    }
                    scope.launch {
                        state = store.beginAction(state, "output-execute")
                        state = store.cleanupOutputs(state)
                    }
                },
            )
        },
    )
}

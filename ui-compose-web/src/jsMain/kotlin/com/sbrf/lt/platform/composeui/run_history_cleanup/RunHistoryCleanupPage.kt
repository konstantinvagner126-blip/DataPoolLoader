package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
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
    var state by remember { mutableStateOf(RunHistoryCleanupPageState()) }
    val runtimeContext = state.runtimeContext
    val callbacks = runHistoryCleanupPageCallbacks(
        store = store,
        scope = rememberCoroutineScope(),
        currentState = { state },
        setState = { state = it },
    )

    RunHistoryCleanupPageEffects(
        store = store,
        currentState = { state },
        setState = { state = it },
    )

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
            RunHistoryCleanupPageContent(
                state = state,
                callbacks = callbacks,
            )
        },
    )
}

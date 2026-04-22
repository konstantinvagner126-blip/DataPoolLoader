package com.sbrf.lt.platform.composeui.module_sync

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
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeModuleSyncPage(
    api: ModuleSyncApi = remember { ModuleSyncApiClient() },
) {
    val store = remember(api) { ModuleSyncStore(api) }
    var state by remember { mutableStateOf(ModuleSyncPageState()) }
    val callbacks = moduleSyncPageCallbacks(
        store = store,
        scope = rememberCoroutineScope(),
        currentState = { state },
        setState = { state = it },
    )

    ModuleSyncPageEffects(
        store = store,
        currentState = { state },
        setState = { state = it },
    )

    PageScaffold(
        eyebrow = "Режим базы данных",
        title = "Импорт модулей из файлов",
        subtitle = "Синхронизация файловых модулей из каталога apps в базу данных. Создание модулей на основе application.yml и SQL-ресурсов.",
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
                }) { Text("Импорт из файлов") }
            }
        },
        content = {
            ModuleSyncPageContent(
                state = state,
                callbacks = callbacks,
            )
        },
    )
}

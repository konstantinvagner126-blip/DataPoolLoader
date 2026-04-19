package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleEditorNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) {
            Text(label)
        }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) {
        Text(label)
    }
}

@Composable
internal fun ModuleEditorHeroArt(storage: String) {
    if (storage == "database") {
        DatabaseModuleHeroArt()
    } else {
        FilesModuleHeroArt()
    }
}

@Composable
internal fun DatabaseModuleHeroArt() {
    Div({ classes("platform-stage") }) {
        Div({ classes("platform-node", "platform-node-db") }) { Text("POSTGRESQL") }
        Div({ classes("platform-node", "platform-node-kafka") }) { Text("REGISTRY") }
        Div({ classes("platform-node", "platform-node-pool") }) { Text("MODULES") }
        Div({ classes("platform-core") }) {
            Div({ classes("platform-core-title") }) { Text("DB") }
            Div({ classes("platform-core-subtitle") }) { Text("MODULE STORE") }
        }
        Div({ classes("platform-rail", "platform-rail-db") }) { Span({ classes("platform-packet", "packet-db") }) }
        Div({ classes("platform-rail", "platform-rail-kafka") }) { Span({ classes("platform-packet", "packet-kafka") }) }
        Div({ classes("platform-rail", "platform-rail-pool") }) { Span({ classes("platform-packet", "packet-pool") }) }
    }
}

@Composable
internal fun FilesModuleHeroArt() {
    Div({ classes("flow-stage") }) {
        listOf("DB1", "DB2", "DB3", "DB4", "DB5").forEachIndexed { index, label ->
            Div({ classes("source-node", "source-node-${index + 1}") }) { Text(label) }
        }
        repeat(5) { index ->
            Div({ classes("flow-line", "flow-line-${index + 1}") }) {
                Span({ classes("flow-dot", "dot-${index + 1}") })
            }
        }
        Div({ classes("merge-hub") }) {
            Div({ classes("merge-title") }) { Text("DATAPOOL") }
        }
    }
}

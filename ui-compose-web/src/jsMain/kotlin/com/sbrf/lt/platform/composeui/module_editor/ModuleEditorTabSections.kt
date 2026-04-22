package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
internal fun TabNavigation(
    activeTab: ModuleEditorTab,
    onTabSelect: (ModuleEditorTab) -> Unit,
) {
    Ul({ classes("nav", "nav-tabs", "mb-3") }) {
        ModuleEditorTab.entries.forEach { tab ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (activeTab == tab) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onTabSelect(tab) }
                }) {
                    Text(tab.label)
                }
            }
        }
    }
}

package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text

internal fun loadSectionExpanded(
    sectionStateKey: String?,
    defaultExpanded: Boolean,
): Boolean {
    if (sectionStateKey == null) {
        return defaultExpanded
    }
    return runCatching { window.localStorage.getItem(sectionStateKey) }
        .getOrNull()
        ?.let { storedValue -> storedValue == "true" }
        ?: defaultExpanded
}

internal fun saveSectionExpanded(
    sectionStateKey: String?,
    expanded: Boolean,
) {
    if (sectionStateKey == null) {
        return
    }
    runCatching { window.localStorage.setItem(sectionStateKey, expanded.toString()) }
}

@Composable
internal fun SectionExpandToggleButton(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Button(attrs = {
        classes("btn", "btn-outline-secondary", "btn-sm", "config-section-toggle")
        attr("type", "button")
        onClick { onToggle() }
    }) {
        Text(if (expanded) "Свернуть" else "Развернуть")
    }
}

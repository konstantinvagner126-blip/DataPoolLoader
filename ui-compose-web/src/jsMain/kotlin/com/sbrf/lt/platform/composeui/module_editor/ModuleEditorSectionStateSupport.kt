package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.browser.window

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

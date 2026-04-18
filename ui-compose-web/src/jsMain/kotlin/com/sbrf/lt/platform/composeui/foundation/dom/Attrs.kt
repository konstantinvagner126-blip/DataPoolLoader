package com.sbrf.lt.platform.composeui.foundation.dom

import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.Element

fun <T : Element> AttrsScope<T>.classes(
    vararg names: String,
) {
    val classNames = buildList {
        names.forEach { name ->
            val normalized = name.trim()
            if (normalized.isNotEmpty()) {
                add(normalized)
            }
        }
    }
    if (classNames.isNotEmpty()) {
        this.classes(classNames)
    }
}

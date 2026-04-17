package com.sbrf.lt.platform.composeui.foundation.dom

import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.Element

fun <T : Element> AttrsScope<T>.classes(
    first: String,
    vararg rest: String,
) {
    attr("class", listOf(first, *rest).joinToString(" "))
}

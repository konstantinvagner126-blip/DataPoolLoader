package com.sbrf.lt.platform.composeui

import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        ComposeSpikeApp()
    }
}

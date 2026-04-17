package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun AlertBanner(
    text: String,
    level: String,
) {
    Div({
        classes("alert", "alert-$level", "mt-3", "mb-4")
    }) {
        Text(text)
    }
}

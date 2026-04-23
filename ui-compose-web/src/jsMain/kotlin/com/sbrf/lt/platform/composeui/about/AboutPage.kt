package com.sbrf.lt.platform.composeui.about

import androidx.compose.runtime.Composable
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
fun ComposeAboutPage() {
    PageScaffold(
        eyebrow = "MLP Platform",
        title = "О проекте",
        subtitle = "Команда разработки проекта.",
        heroClassNames = listOf("hero-card-compact"),
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
                }) { Text("О проекте") }
            }
        },
        content = {
            AboutPageContent()
        },
    )
}

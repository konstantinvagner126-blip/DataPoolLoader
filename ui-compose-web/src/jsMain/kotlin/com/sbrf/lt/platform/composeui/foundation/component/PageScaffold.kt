package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Footer
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun PageScaffold(
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
    heroArt: (@Composable () -> Unit)? = null,
    heroClassNames: List<String> = emptyList(),
    heroCopyClassNames: List<String> = emptyList(),
    heroHeader: (@Composable () -> Unit)? = null,
) {
    Div({ classes("container-fluid", "py-4", "compose-home-root") }) {
        Div({ classes("hero-card", "mb-4", *heroClassNames.toTypedArray()) }) {
            Div({
                if (heroCopyClassNames.isNotEmpty()) {
                    classes(*heroCopyClassNames.toTypedArray())
                }
            }) {
                if (heroHeader != null) {
                    heroHeader()
                }
                Div({ classes("eyebrow") }) { Text(eyebrow) }
                H1({ classes("display-6", "mb-1") }) { Text(title) }
                P({ classes("text-secondary", "mb-0") }) { Text(subtitle) }
            }
            if (heroArt != null) {
                Div({
                    classes("hero-art")
                    attr("aria-hidden", "true")
                }) {
                    heroArt()
                }
            }
        }

        content()

        Footer({ classes("footer-note", "text-center", "mt-4") }) {
            Text("Разработано командой MLP")
        }
    }
}

package com.sbrf.lt.platform.composeui.about

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun AboutPageContent() {
    Div({ classes("about-developer-grid") }) {
        AboutDeveloperCard(
            name = "Вагнер Константин",
            alias = "kwdev",
            accentClass = "about-developer-card-primary",
            motorcycleClassNames = listOf("about-sportbike-black"),
        )
        AboutDeveloperCard(
            name = "Родионов Сергей",
            alias = "darkelf",
            accentClass = "about-developer-card-secondary",
            motorcycleClassNames = listOf("about-sportbike-black", "about-sportbike-delayed"),
        )
    }
}

@Composable
private fun AboutDeveloperCard(
    name: String,
    alias: String,
    accentClass: String,
    motorcycleClassNames: List<String>,
) {
    Div({ classes("panel", "about-card", "about-developer-card", accentClass) }) {
        Div({ classes("home-card-label") }) { Text("Разработчик") }
        Div({ classes("about-developer-head") }) {
            Div {
                H3({ classes("about-card-title", "mb-1") }) {
                    Text(name)
                }
                Div({ classes("about-developer-alias") }) {
                    Text(alias)
                }
            }
        }
        AboutMotorcycleAnimation(motorcycleClassNames)
    }
}

@Composable
private fun AboutMotorcycleAnimation(motorcycleClassNames: List<String>) {
    Div({ classes("about-motorcycle-stage") }) {
        Div({ classes("about-motorcycle-track") })
        Div({ classes("about-motorcycle", *motorcycleClassNames.toTypedArray()) }) {
            Div({ classes("about-motorcycle-rider") })
            Div({ classes("about-motorcycle-tail") })
            Div({ classes("about-motorcycle-seat") })
            Div({ classes("about-motorcycle-body") })
            Div({ classes("about-motorcycle-fairing") })
            Div({ classes("about-motorcycle-windscreen") })
            Div({ classes("about-motorcycle-front-fork") })
            Div({ classes("about-motorcycle-swingarm") })
            Div({ classes("about-motorcycle-exhaust") })
            Div({ classes("about-motorcycle-wheel", "about-motorcycle-wheel-back") })
            Div({ classes("about-motorcycle-wheel", "about-motorcycle-wheel-front") })
        }
    }
}

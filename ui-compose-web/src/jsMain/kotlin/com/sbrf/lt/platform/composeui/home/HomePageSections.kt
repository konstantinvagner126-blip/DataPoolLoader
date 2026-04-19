package com.sbrf.lt.platform.composeui.home

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun HeroCard() {
    Div({ classes("hero-card", "mb-4") }) {
        Div {
            Div({ classes("eyebrow") }) { Text("MLP Platform") }
            H1({ classes("display-6", "mb-1") }) {
                Text("Load Testing Data Platform")
            }
            P({ classes("text-secondary", "mb-0") }) {
                Text("Единая точка входа для подготовки данных, запуска модулей БД и Kafka, ручной работы с источниками и служебных операций при нагрузочном тестировании микросервисов.")
            }
        }

        Div({
            classes("hero-art")
            attr("aria-hidden", "true")
        }) {
            Div({ classes("platform-stage") }) {
                Div({ classes("platform-node", "platform-node-db") }) { Text("DB") }
                Div({ classes("platform-node", "platform-node-kafka") }) { Text("KAFKA") }
                Div({ classes("platform-node", "platform-node-pool") }) { Text("DATAPOOL") }
                Div({ classes("platform-core") }) {
                    Div({ classes("platform-core-title") }) { Text("LTP") }
                    Div({ classes("platform-core-subtitle") }) { Text("MICROSERVICES") }
                }
                Rail("platform-rail-db", "packet-db")
                Rail("platform-rail-kafka", "packet-kafka")
                Rail("platform-rail-pool", "packet-pool")
            }
        }
    }
}

@Composable
internal fun Rail(
    railClass: String,
    packetClass: String,
) {
    Div({ classes("platform-rail", railClass) }) {
        Span({ classes("platform-packet", packetClass) })
    }
}

@Composable
internal fun ModeCard(
    label: String,
    title: String,
    text: String,
    action: String,
    href: String,
    enabled: Boolean,
    disabledText: String,
) {
    if (enabled) {
        SimpleCard(label, title, text, action, href, "home-mode-card")
        return
    }

    Div({
        classes("home-card", "home-mode-card", "home-card-disabled")
        attr("aria-disabled", "true")
        attr("title", disabledText)
    }) {
        CardBody(label, title, text, "Недоступно в текущем режиме")
    }
}

@Composable
internal fun SimpleCard(
    label: String,
    title: String,
    text: String,
    action: String,
    href: String,
    vararg extraClasses: String,
) {
    A(
        attrs = {
            classes("home-card", *extraClasses)
            href(href)
            target(ATarget.Self)
        },
    ) {
        CardBody(label, title, text, action)
    }
}

@Composable
internal fun CardBody(
    label: String,
    title: String,
    text: String,
    action: String,
) {
    if (label.isNotBlank()) {
        Div({ classes("home-card-label") }) { Text(label) }
    }
    Div({ classes("home-card-title") }) { Text(title) }
    Div({ classes("home-card-text") }) { Text(text) }
    Div({ classes("home-card-action") }) { Text(action) }
}

package com.sbrf.lt.platform.composeui.home

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun HomePlatformHeader() {
    Div({ classes("home-platform-title-card") }) {
        Div({
            classes("home-platform-mark")
            attr("aria-hidden", "true")
        })
        Div {
            Div({ classes("eyebrow") }) { Text("MLP Platform") }
            H1({ classes("home-platform-title") }) {
                Text("Платформа инструментов тестирования микросервисов")
            }
        }
    }
}

@Composable
internal fun LauncherGroup(
    label: String? = null,
    title: String,
    modifierClass: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Div({
        if (modifierClass.isNullOrBlank()) {
            classes("home-launcher-group")
        } else {
            classes("home-launcher-group", modifierClass)
        }
    }) {
        Div({ classes("home-group-intro") }) {
            Div {
                if (!label.isNullOrBlank()) {
                    Div({ classes("eyebrow") }) { Text(label) }
                }
                Div({ classes("home-group-title") }) { Text(title) }
            }
            if (headerAction != null) {
                headerAction()
            }
        }
        content()
    }
}

@Composable
internal fun ModeCard(
    title: String,
    text: String,
    action: String,
    href: String,
    enabled: Boolean,
    disabledText: String,
    icon: String,
    iconTone: String? = null,
    chip: String,
    chipTone: String,
) {
    if (enabled) {
        SimpleCard(
            title = title,
            text = text,
            action = action,
            href = href,
            icon = icon,
            iconTone = iconTone,
            chip = chip,
            chipTone = chipTone,
            "home-mode-card",
        )
        return
    }

    Div({
        classes("home-tool-card", "home-mode-card", "home-card-disabled")
        attr("aria-disabled", "true")
        attr("title", disabledText)
    }) {
        CardBody(
            title = title,
            text = text,
            action = "Недоступно в текущем режиме",
            icon = icon,
            iconTone = iconTone,
            chip = "недоступно",
            chipTone = "lock",
        )
    }
}

@Composable
internal fun SimpleCard(
    title: String,
    text: String,
    action: String,
    href: String,
    icon: String,
    iconTone: String? = null,
    chip: String? = null,
    chipTone: String? = null,
    vararg extraClasses: String,
) {
    A(
        attrs = {
            classes("home-tool-card", *extraClasses)
            href(href)
            target(ATarget.Self)
        },
    ) {
        CardBody(
            title = title,
            text = text,
            action = action,
            icon = icon,
            iconTone = iconTone,
            chip = chip,
            chipTone = chipTone,
        )
    }
}

@Composable
internal fun CardBody(
    title: String,
    text: String,
    action: String,
    icon: String,
    iconTone: String? = null,
    chip: String? = null,
    chipTone: String? = null,
) {
    Div {
        Div({ classes("home-tool-top") }) {
            Div({
                if (iconTone.isNullOrBlank()) {
                    classes("home-tool-icon")
                } else {
                    classes("home-tool-icon", iconTone)
                }
            }) {
                Text(icon)
            }
            if (!chip.isNullOrBlank()) {
                Span({
                    if (chipTone.isNullOrBlank()) {
                        classes("home-chip")
                    } else {
                        classes("home-chip", chipTone)
                    }
                }) {
                    Text(chip)
                }
            }
        }
        Div({ classes("home-card-title") }) { Text(title) }
        Div({ classes("home-card-text") }) { Text(text) }
    }
    Div({ classes("home-card-action") }) {
        Span { Text(action) }
        Span({ classes("home-card-arrow") }) { Text("->") }
    }
}

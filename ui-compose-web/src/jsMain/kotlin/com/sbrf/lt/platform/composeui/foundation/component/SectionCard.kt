package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Div({ classes("panel", "mb-4") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-start", "justify-content-between", "gap-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text(title) }
                if (!subtitle.isNullOrBlank()) {
                    Div({ classes("text-secondary", "small") }) { Text(subtitle) }
                }
            }
            if (actions != null) {
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    actions()
                }
            }
        }
        Div({ classes("mt-3") }) {
            content()
        }
    }
}

@Composable
fun LoadingStateCard(
    title: String = "Загрузка",
    text: String = "Данные загружаются.",
) {
    SectionCard(title = title) {
        P({ classes("text-secondary", "mb-0") }) {
            Text(text)
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    text: String,
) {
    SectionCard(title = title) {
        P({ classes("text-secondary", "mb-0") }) {
            Text(text)
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    tone: String = "secondary",
) {
    SpanBadge(
        className = when (tone.lowercase()) {
            "success" -> "badge text-bg-success"
            "danger" -> "badge text-bg-danger"
            "warning" -> "badge text-bg-warning"
            "primary" -> "badge text-bg-primary"
            else -> "badge text-bg-secondary"
        },
        text = text,
    )
}

@Composable
private fun SpanBadge(
    className: String,
    text: String,
) {
    org.jetbrains.compose.web.dom.Span({ classes(*className.split(" ").filter { it.isNotBlank() }.toTypedArray()) }) {
        Text(text)
    }
}

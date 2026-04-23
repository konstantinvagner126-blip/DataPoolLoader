package com.sbrf.lt.platform.composeui.about

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun AboutPageContent() {
    Div({ classes("about-grid") }) {
        Div({ classes("panel", "about-card", "about-card-wide") }) {
            Div({ classes("home-card-label") }) { Text("Назначение") }
            H3({ classes("about-card-title") }) {
                Text("Load Testing Data Platform")
            }
            P({ classes("about-card-text") }) {
                Text("Проект объединяет подготовку данных, запуск модулей, SQL-консоль и служебные операции в одном локальном инженерном интерфейсе.")
            }
            Div({ classes("about-chip-row") }) {
                AboutChip("Локальный режим")
                AboutChip("Надежность")
                AboutChip("Расширяемость")
                AboutChip("Инженерный UI")
            }
        }

        Div({ classes("panel", "about-card") }) {
            Div({ classes("home-card-label") }) { Text("Фокус") }
            H3({ classes("about-card-title") }) {
                Text("Архитектура важнее случайной скорости")
            }
            P({ classes("about-card-text") }) {
                Text("Проект развивается через явные контракты, разделение слоев и safety-инварианты для long-running операций и SQL-консоли.")
            }
        }
    }

    Div({ classes("about-developer-grid") }) {
        AboutDeveloperCard(
            name = "Вагнер Константин",
            alias = "kwdev",
            accentClass = "about-developer-card-primary",
            motorcycleClass = "about-sportbike-black",
        )
        AboutDeveloperCard(
            name = "Родионов Сергей",
            alias = "darkelf",
            accentClass = "about-developer-card-secondary",
            motorcycleClass = "about-sportbike-black about-sportbike-delayed",
        )
    }
}

@Composable
private fun AboutChip(text: String) {
    Span({ classes("about-chip") }) {
        Text(text)
    }
}

@Composable
private fun AboutDeveloperCard(
    name: String,
    alias: String,
    accentClass: String,
    motorcycleClass: String,
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
            Div({ classes("about-developer-role") }) {
                Text("Engineering")
            }
        }
        P({ classes("about-card-text", "mb-3") }) {
            Text("Участник команды разработки MLP. На карточке ниже — декоративный черный спортивный мотоцикл, который едет на заднем колесе.")
        }
        AboutMotorcycleAnimation(motorcycleClass)
    }
}

@Composable
private fun AboutMotorcycleAnimation(motorcycleClass: String) {
    Div({ classes("about-motorcycle-stage") }) {
        Div({ classes("about-motorcycle-track") })
        Div({ classes("about-motorcycle", motorcycleClass) }) {
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

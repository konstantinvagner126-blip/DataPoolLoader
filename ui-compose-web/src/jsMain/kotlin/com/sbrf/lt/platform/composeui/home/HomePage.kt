package com.sbrf.lt.platform.composeui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.RuntimeModeSwitch
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Footer
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeHomePage(
    api: HomePageApi = remember { HomePageApiClient() },
) {
    val initialModeAccessError = remember(window.location.search) {
        parseModeAccessError(window.location.search)
    }
    val store = remember(api, initialModeAccessError) {
        HomePageStore(api, initialModeAccessError)
    }
    val scope = rememberCoroutineScope()
    var state by remember(initialModeAccessError) {
        mutableStateOf(HomePageState(modeAccessError = initialModeAccessError))
    }

    LaunchedEffect(store) {
        state = store.startReload(state)
        state = store.load()
    }

    val runtime = state.homeData?.runtimeContext
    val isRequestedDb = runtime?.requestedMode == ModuleStoreMode.DATABASE
    val isEffectiveDb = runtime?.effectiveMode == ModuleStoreMode.DATABASE
    val effectiveModeLabel = runtime?.effectiveMode?.label ?: "Загрузка..."
    val canRequestDatabaseMode = runtime?.database?.available == true
    val isToggleDisabled = state.loading || state.savingMode || (!canRequestDatabaseMode && !isRequestedDb)
    val modeAccessError = state.modeAccessError

    Div({
        classes("container-fluid", "py-4", "compose-home-root")
    }) {
        HeroCard()

        when {
            state.errorMessage != null -> {
                AlertBanner(
                    text = state.errorMessage ?: "",
                    level = "warning",
                )
            }
            modeAccessError != null -> {
                AlertBanner(
                    text = buildModeAccessAlertText(modeAccessError, runtime),
                    level = "warning",
                )
            }
        }

        Div({ classes("home-grid") }) {
            Div({ classes("home-group-card") }) {
                Div({ classes("home-group-header") }) {
                    Div({ classes("home-group-header-main") }) {
                        Div({ classes("home-card-label") }) { Text("Модули") }
                        Div({ classes("home-group-title") }) { Text("Загрузка дата пулов") }
                        Div({ classes("home-group-text") }) {
                            Text("Выбери источник конфигурации модулей и открой соответствующий режим работы.")
                        }
                    }

                    Div({ classes("db-mode-indicator") }) {
                        Div({ classes("db-mode-indicator-inner") }) {
                            Span({
                                classes(
                                    "db-mode-indicator-dot",
                                    if (isEffectiveDb) "db-mode-active" else "db-mode-inactive",
                                )
                            })
                            Span { Text("Режим: $effectiveModeLabel") }
                            val modeStatusText = buildModeStatusText(runtime)
                            if (modeStatusText.isNotBlank()) {
                                Span({ classes("db-mode-status") }) { Text(modeStatusText) }
                            }
                            RuntimeModeSwitch(
                                checked = isRequestedDb,
                                disabled = isToggleDisabled,
                                onToggle = {
                                    scope.launch {
                                        state = store.startModeSave(state)
                                        state = store.updateMode(
                                            currentState = state,
                                            mode = if (isRequestedDb) {
                                                ModuleStoreMode.FILES
                                            } else {
                                                ModuleStoreMode.DATABASE
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                Div({ classes("home-group-grid") }) {
                    ModeCard(
                        label = "Режим",
                        title = "Файловый режим",
                        text = "Запуск модулей из файлов проекта, редактирование YAML и SQL, история запусков и итоги выполнения.",
                        action = "Открыть файловые модули",
                        href = "/modules",
                        enabled = !isEffectiveDb,
                        disabledText = "Страница файловых модулей доступна только в режиме «Файлы».",
                    )
                    ModeCard(
                        label = "Режим",
                        title = "DB режим",
                        text = "Управление модулями в базе данных: просмотр, редактирование, публикация и запуск.",
                        action = "Открыть DB-модули",
                        href = "/db-modules",
                        enabled = isEffectiveDb,
                        disabledText = runtime?.fallbackReason
                            ?: "Страница модулей из базы данных доступна только в режиме «База данных».",
                    )
                }
            }

            SimpleCard(
                label = "SQL",
                title = "SQL-консоль",
                text = "Ручное выполнение SQL по выбранным источникам, просмотр результатов по shard/source и служебные операции.",
                action = "Открыть SQL-консоль",
                href = "/compose-sql-console",
            )

            SimpleCard(
                label = "Справка",
                title = "Справка",
                text = "Подробные инструкции по каждому модулю, рекомендации по запуску и пояснения по ключевым параметрам конфигурации.",
                action = "Открыть справку",
                href = "/help",
            )
        }

        Footer({ classes("footer-note", "text-center", "mt-4") }) {
            Text("Разработано командой MLP")
        }
    }
}

@Composable
private fun HeroCard() {
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
private fun Rail(
    railClass: String,
    packetClass: String,
) {
    Div({ classes("platform-rail", railClass) }) {
        Span({ classes("platform-packet", packetClass) })
    }
}

@Composable
private fun ModeCard(
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
private fun SimpleCard(
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
private fun CardBody(
    label: String,
    title: String,
    text: String,
    action: String,
) {
    Div({ classes("home-card-label") }) { Text(label) }
    Div({ classes("home-card-title") }) { Text(title) }
    Div({ classes("home-card-text") }) { Text(text) }
    Div({ classes("home-card-action") }) { Text(action) }
}

private fun buildModeStatusText(runtime: RuntimeContext?): String {
    if (runtime == null) {
        return ""
    }
    val parts = mutableListOf<String>()
    parts += if (runtime.database.available) {
        "PostgreSQL доступен"
    } else {
        "PostgreSQL недоступен"
    }
    if (runtime.requestedMode != runtime.effectiveMode) {
        parts += "запрошен режим ${if (runtime.requestedMode == ModuleStoreMode.DATABASE) "«База данных»" else "«Файлы»"}"
    }
    return parts.joinToString(" · ")
}

private fun parseModeAccessError(search: String): String? {
    if (search.isBlank()) {
        return null
    }
    return js("new URLSearchParams(search).get('modeAccessError')") as String?
}

private fun buildModeAccessAlertText(
    modeAccessError: String,
    runtime: RuntimeContext?,
): String {
    return when (modeAccessError) {
        "modules" -> "Страница файловых модулей доступна только в режиме «Файлы»."
        "db-modules",
        "db-sync",
        -> {
            val suffix = runtime?.fallbackReason?.let {
                " Текущая причина переключения в файловый режим: $it"
            }.orEmpty()
            "Страницы режима «База данных» доступны только в режиме «База данных».$suffix"
        }
        else -> "Страница недоступна в текущем режиме UI."
    }
}

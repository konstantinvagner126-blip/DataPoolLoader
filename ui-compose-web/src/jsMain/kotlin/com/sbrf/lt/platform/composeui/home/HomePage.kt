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
import com.sbrf.lt.platform.composeui.model.label
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Footer
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
        Div({ classes("home-visual-shell") }) {
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
                            Div({ classes("home-card-label") }) { Text("Нагрузочное тестирование") }
                        }
                    }

                    Div({ classes("home-group-stack") }) {
                        Div({ classes("home-subgroup-card") }) {
                            Div({ classes("home-subgroup-header") }) {
                                Div {
                                    Div({ classes("home-card-label") }) { Text("Обновление датапулов") }
                                    Div({ classes("home-subgroup-title") }) { Text("Запуск по модулям") }
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
                                    label = "",
                                    title = "Файловый режим",
                                    text = "Запуск модулей из файлов проекта, редактирование YAML и SQL, история запусков и итоги выполнения.",
                                    action = "Открыть файловые модули",
                                    href = "/modules",
                                    enabled = !isEffectiveDb,
                                    disabledText = "Страница файловых модулей доступна только в режиме «Файлы».",
                                )
                                ModeCard(
                                    label = "",
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
                            label = "Сервис",
                            title = "Очистка истории запусков",
                            text = "Preview и controlled cleanup истории запусков в текущем режиме без смешения с output-retention.",
                            action = "Открыть очистку истории",
                            href = "/run-history-cleanup",
                        )
                    }
                }

                SimpleCard(
                    label = "SQL",
                    title = "SQL-консоль",
                    text = "Ручное выполнение SQL по выбранным источникам, просмотр результатов по shard/source и служебные операции.",
                    action = "Открыть SQL-консоль",
                    href = "/sql-console",
                )

                SimpleCard(
                    label = "Справка",
                    title = "Справка",
                    text = "Подробные инструкции по каждому модулю, рекомендации по запуску и пояснения по ключевым параметрам конфигурации.",
                    action = "Открыть справку",
                    href = "/help",
                )

                SimpleCard(
                    label = "Команда",
                    title = "О проекте",
                    text = "Краткое описание Load Testing Data Platform, список разработчиков и отдельная информационная страница проекта.",
                    action = "Открыть страницу проекта",
                    href = "/about",
                )
            }
        }

        Footer({ classes("footer-note", "text-center", "mt-4") }) {
            Text("Разработано командой MLP")
        }
    }
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
        parts += "запрошен режим «${runtime.requestedMode.label}»"
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
): String =
    when (modeAccessError) {
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

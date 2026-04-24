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
    val canRequestDatabaseMode = runtime?.database?.available == true
    val isToggleDisabled = state.loading || state.savingMode || (!canRequestDatabaseMode && !isRequestedDb)
    val modeAccessError = state.modeAccessError

    Div({
        classes("container-fluid", "py-4", "compose-home-root")
    }) {
        Div({ classes("home-visual-shell") }) {
            HomePlatformHeader()

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
                LauncherGroup(
                    label = "Нагрузочное тестирование",
                    title = "Датапулы",
                    headerAction = {
                        Div({ classes("home-mode-control") }) {
                            Div({ classes("home-mode-copy") }) {
                                Div({ classes("home-mode-title") }) { Text(buildModeTitleText(runtime)) }
                                val modeStatusText = buildModeStatusText(runtime)
                                if (modeStatusText.isNotBlank()) {
                                    Div({ classes("home-mode-status") }) { Text(modeStatusText) }
                                }
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
                    },
                ) {
                    Div({ classes("home-card-grid") }) {
                        ModeCard(
                            title = "Файловые модули",
                            text = "Редактирование модулей из файлов проекта, SQL-ресурсов и локальной истории запусков.",
                            action = "Открыть файловые модули",
                            href = "/modules",
                            enabled = !isEffectiveDb,
                            disabledText = "Страница файловых модулей доступна только в режиме «Файлы».",
                            icon = "YML",
                            chip = if (!isEffectiveDb) "активно" else "недоступно",
                            chipTone = if (!isEffectiveDb) "ok" else "lock",
                        )
                        ModeCard(
                            title = "DB-модули",
                            text = "Просмотр, редактирование, публикация и запуск модулей, которые хранятся в PostgreSQL.",
                            action = "Открыть DB-модули",
                            href = "/db-modules",
                            enabled = isEffectiveDb,
                            disabledText = runtime?.fallbackReason
                                ?: "Страница модулей из базы данных доступна только в режиме «База данных».",
                            icon = "DB",
                            iconTone = "green",
                            chip = if (isEffectiveDb) "активно" else "недоступно",
                            chipTone = if (isEffectiveDb) "ok" else "lock",
                        )
                        SimpleCard(
                            title = "Очистка истории",
                            text = "Preview и controlled cleanup истории запусков без смешения с output-retention.",
                            action = "Открыть очистку",
                            href = "/run-history-cleanup",
                            icon = "HST",
                            iconTone = "amber",
                            chip = "controlled",
                            chipTone = "warn",
                        )
                    }
                }

                LauncherGroup(
                    label = "Работа с данными",
                    title = "Инструменты",
                ) {
                    Div({ classes("home-card-grid", "home-card-grid-two") }) {
                        SimpleCard(
                            title = "SQL-консоль",
                            text = "Выполнение SQL по выбранным источникам, result grid, diff-режим, история запусков и настройки sources.",
                            action = "Открыть SQL-консоль",
                            href = "/sql-console",
                            icon = "SQL",
                            chip = "sources",
                            chipTone = "ok",
                        )
                        SimpleCard(
                            title = "Kafka-инструмент",
                            text = "Кластеры из конфига, топики, consumer groups, lag, bounded read сообщений и controlled produce.",
                            action = "Открыть Kafka-инструмент",
                            href = "/kafka",
                            icon = "KFK",
                            iconTone = "green",
                            chip = "cluster-first",
                            chipTone = "ok",
                        )
                    }
                }

                LauncherGroup(
                    title = "Справка",
                ) {
                    Div({ classes("home-card-grid", "home-card-grid-two") }) {
                        SimpleCard(
                            title = "Справка",
                            text = "Короткие инструкции по рабочим экранам, режимам запуска и ключевым параметрам конфигурации.",
                            action = "Открыть справку",
                            href = "/help",
                            icon = "DOC",
                            iconTone = "amber",
                            chip = "readme",
                        )
                        SimpleCard(
                            title = "О проекте",
                            text = "Только карточки разработчиков и минимальная техническая атрибуция без лишнего explanatory content.",
                            action = "Открыть страницу проекта",
                            href = "/about",
                            icon = "MLP",
                            chip = "карточки",
                        )
                    }
                }
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

private fun buildModeTitleText(runtime: RuntimeContext?): String =
    when (runtime?.effectiveMode) {
        ModuleStoreMode.DATABASE -> "DB режим активен"
        ModuleStoreMode.FILES -> "Файловый режим активен"
        null -> "Режим загружается"
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

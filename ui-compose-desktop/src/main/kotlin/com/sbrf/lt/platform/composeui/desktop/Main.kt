package com.sbrf.lt.platform.composeui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sbrf.lt.platform.composeui.home.HomePageData
import com.sbrf.lt.platform.composeui.home.HomePageState
import com.sbrf.lt.platform.composeui.home.HomePageStore
import com.sbrf.lt.platform.composeui.home.label
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsRouteState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsStore
import com.sbrf.lt.platform.composeui.module_runs.translateLaunchSource
import com.sbrf.lt.platform.composeui.module_runs.translateRunStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.coroutines.launch

fun main() = application {
    val config = remember { DesktopRuntimeConfig.load() }
    val httpClient = remember(config.serverBaseUrl) { DesktopHttpJsonClient(config.serverBaseUrl) }
    val homeApi = remember(httpClient) { DesktopHomePageApi(httpClient) }
    val homeStore = remember(httpClient) { HomePageStore(api = homeApi, modeAccessError = null) }
    val runsApi = remember(httpClient) { DesktopModuleRunsApi(httpClient) }
    val runsStore = remember(httpClient) { ModuleRunsStore(runsApi) }
    val windowActions = remember(config.serverBaseUrl) { DefaultDesktopWindowActions(config.serverBaseUrl, ::exitApplication) }
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf(DesktopNavigationState()) }
    var homeState by remember { mutableStateOf(HomePageState()) }
    var runsState by remember { mutableStateOf(ModuleRunsPageState(loading = false, historyLimit = navigation.runsHistoryLimit)) }

    LaunchedEffect(homeStore) {
        homeState = homeStore.startReload(homeState)
        homeState = homeStore.load()
    }

    LaunchedEffect(navigation.screen, navigation.runsStorage, navigation.runsModuleId, navigation.runsHistoryLimit) {
        if (navigation.screen != DesktopScreen.RUNS || navigation.runsModuleId.isBlank()) {
            return@LaunchedEffect
        }
        runsState = runsStore.startLoading(runsState.copy(historyLimit = navigation.runsHistoryLimit))
        runsState = runsStore.load(
            route = ModuleRunsRouteState(navigation.runsStorage, navigation.runsModuleId),
            historyLimit = navigation.runsHistoryLimit,
        )
    }

    Window(
        onCloseRequest = windowActions::close,
        title = "DataPoolLoader Compose Desktop",
    ) {
        MaterialTheme {
            DesktopApp(
                serverBaseUrl = config.serverBaseUrl,
                navigation = navigation,
                homeState = homeState,
                runsState = runsState,
                onNavigate = { screen -> navigation = navigation.copy(screen = screen) },
                onUpdateRunsStorage = { value -> navigation = navigation.copy(runsStorage = value) },
                onUpdateRunsModule = { value -> navigation = navigation.copy(runsModuleId = value) },
                onUpdateRunsHistoryLimit = { value -> navigation = navigation.copy(runsHistoryLimit = value) },
                onReload = {
                    scope.launch {
                        when (navigation.screen) {
                            DesktopScreen.HOME -> {
                                homeState = homeStore.startReload(homeState)
                                homeState = homeStore.load()
                            }
                            DesktopScreen.RUNS -> {
                                runsState = runsStore.startLoading(runsState.copy(historyLimit = navigation.runsHistoryLimit))
                                runsState = runsStore.load(
                                    route = ModuleRunsRouteState(navigation.runsStorage, navigation.runsModuleId),
                                    historyLimit = navigation.runsHistoryLimit,
                                )
                            }
                        }
                    }
                },
                onSwitchMode = { mode ->
                    scope.launch {
                        homeState = homeStore.startModeSave(homeState)
                        homeState = homeStore.updateMode(homeState, mode)
                    }
                },
                onSelectRun = { runId ->
                    scope.launch {
                        runsState = runsStore.startLoading(runsState)
                        runsState = runsStore.selectRun(
                            current = runsState,
                            route = ModuleRunsRouteState(navigation.runsStorage, navigation.runsModuleId),
                            runId = runId,
                        )
                    }
                },
                windowActions = windowActions,
            )
        }
    }
}

@Composable
private fun DesktopApp(
    serverBaseUrl: String,
    navigation: DesktopNavigationState,
    homeState: HomePageState,
    runsState: ModuleRunsPageState,
    onNavigate: (DesktopScreen) -> Unit,
    onUpdateRunsStorage: (String) -> Unit,
    onUpdateRunsModule: (String) -> Unit,
    onUpdateRunsHistoryLimit: (Int) -> Unit,
    onReload: () -> Unit,
    onSwitchMode: (ModuleStoreMode) -> Unit,
    onSelectRun: (String) -> Unit,
    windowActions: DesktopWindowActions,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("DataPoolLoader Desktop", style = MaterialTheme.typography.h4)
            Text(
                "Desktop shell использует тот же shared слой, что и web, и ходит в живой ui-server по HTTP без browser-specific кода.",
                style = MaterialTheme.typography.body1,
            )
            Text("Endpoint: $serverBaseUrl", style = MaterialTheme.typography.body2)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DesktopNavButton("Главная", navigation.screen == DesktopScreen.HOME) { onNavigate(DesktopScreen.HOME) }
                DesktopNavButton("История запусков", navigation.screen == DesktopScreen.RUNS) { onNavigate(DesktopScreen.RUNS) }
                OutlinedButton(onClick = onReload) { Text("Обновить") }
                OutlinedButton(onClick = windowActions::openWebUi) { Text("Открыть web UI") }
            }

            when (navigation.screen) {
                DesktopScreen.HOME -> DesktopHomeScreen(
                    state = homeState,
                    onSwitchMode = onSwitchMode,
                )
                DesktopScreen.RUNS -> DesktopRunsScreen(
                    state = runsState,
                    navigation = navigation,
                    onUpdateRunsStorage = onUpdateRunsStorage,
                    onUpdateRunsModule = onUpdateRunsModule,
                    onUpdateRunsHistoryLimit = onUpdateRunsHistoryLimit,
                    onSelectRun = onSelectRun,
                )
            }
        }
    }
}

@Composable
private fun DesktopNavButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    if (active) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun DesktopHomeScreen(
    state: HomePageState,
    onSwitchMode: (ModuleStoreMode) -> Unit,
) {
    val data = state.homeData
    if (state.errorMessage != null) {
        Text("Ошибка: ${state.errorMessage}", color = MaterialTheme.colors.error)
    }
    if (data == null) {
        Text("Загрузка desktop shell...")
        return
    }

    RuntimeSection(data)
    CatalogSection(data)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onSwitchMode(ModuleStoreMode.FILES) },
            enabled = !state.savingMode,
        ) {
            Text("Режим ${ModuleStoreMode.FILES.label}")
        }
        OutlinedButton(
            onClick = { onSwitchMode(ModuleStoreMode.DATABASE) },
            enabled = !state.savingMode,
        ) {
            Text("Режим ${ModuleStoreMode.DATABASE.label}")
        }
    }
}

@Composable
private fun DesktopRunsScreen(
    state: ModuleRunsPageState,
    navigation: DesktopNavigationState,
    onUpdateRunsStorage: (String) -> Unit,
    onUpdateRunsModule: (String) -> Unit,
    onUpdateRunsHistoryLimit: (Int) -> Unit,
    onSelectRun: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("История запусков", style = MaterialTheme.typography.h6)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Хранилище:")
                DesktopNavButton("Файлы", navigation.runsStorage == "files") { onUpdateRunsStorage("files") }
                DesktopNavButton("База данных", navigation.runsStorage == "database") { onUpdateRunsStorage("database") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Код модуля", style = MaterialTheme.typography.caption)
                    androidx.compose.material.OutlinedTextField(
                        value = navigation.runsModuleId,
                        onValueChange = onUpdateRunsModule,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Column(modifier = Modifier.width(120.dp)) {
                    Text("Показывать", style = MaterialTheme.typography.caption)
                    androidx.compose.material.OutlinedTextField(
                        value = navigation.runsHistoryLimit.toString(),
                        onValueChange = { onUpdateRunsHistoryLimit(it.toIntOrNull()?.takeIf { value -> value > 0 } ?: navigation.runsHistoryLimit) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
    }

    if (state.errorMessage != null) {
        Text("Ошибка: ${state.errorMessage}", color = MaterialTheme.colors.error)
    }
    if (state.loading && state.session == null) {
        Text("Загрузка истории запусков...")
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(state.session?.moduleTitle ?: "Запуски", style = MaterialTheme.typography.h6)
                val runs = state.history?.runs.orEmpty()
                if (runs.isEmpty()) {
                    Text("Для выбранного модуля запусков пока нет.")
                } else {
                    runs.forEach { run ->
                        val active = run.runId == state.selectedRunId
                        if (active) {
                            Button(onClick = { onSelectRun(run.runId) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${translateRunStatus(run.status)} · ${run.runId}")
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectRun(run.runId) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${translateRunStatus(run.status)} · ${run.runId}")
                            }
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val details = state.selectedRunDetails
                if (details == null) {
                    Text("Выбери запуск выше, чтобы посмотреть детали.")
                } else {
                    Text(details.run.moduleTitle.ifBlank { details.run.moduleId }, style = MaterialTheme.typography.h6)
                    Text("Статус: ${translateRunStatus(details.run.status)}")
                    Text("Источник запуска: ${translateLaunchSource(details.run.launchSourceKind)}")
                    Text("Старт: ${formatDesktopInstant(details.run.startedAt)}")
                    Text("Завершение: ${formatDesktopInstant(details.run.finishedAt)}")
                    Text("Merged rows: ${details.run.mergedRowCount ?: 0}")
                    Text("Target: ${details.run.targetTableName ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Последние события", style = MaterialTheme.typography.subtitle1)
                    if (details.events.isEmpty()) {
                        Text("События пока недоступны.")
                    } else {
                        details.events.takeLast(12).forEach { event ->
                            Text("• ${formatDesktopInstant(event.timestamp)} · ${event.message ?: event.eventType}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeSection(data: HomePageData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Runtime", style = MaterialTheme.typography.h6)
        Text("Запрошенный режим: ${data.runtimeContext.requestedMode.label}")
        Text("Эффективный режим: ${data.runtimeContext.effectiveMode.label}")
        Text("Actor: ${data.runtimeContext.actor.actorDisplayName ?: data.runtimeContext.actor.actorId ?: "не задан"}")
        Text("База данных: ${data.runtimeContext.database.message}")
        if (!data.runtimeContext.fallbackReason.isNullOrBlank()) {
            Text("Fallback: ${data.runtimeContext.fallbackReason}")
        }
    }
}

@Composable
private fun CatalogSection(data: HomePageData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Каталоги", style = MaterialTheme.typography.h6)
        Text("Файловые модули: ${data.filesCatalog.diagnostics.totalModules}")
        Text("DB-модули: ${data.databaseCatalog?.diagnostics?.totalModules ?: 0}")
        Text("appsRoot: ${data.filesCatalog.appsRootStatus.message}")
    }
}

private fun formatDesktopInstant(value: String?): String {
    if (value.isNullOrBlank()) {
        return "-"
    }
    return runCatching {
        java.time.OffsetDateTime.parse(value).toLocalDateTime().toString().replace('T', ' ')
    }.getOrElse { value }
}

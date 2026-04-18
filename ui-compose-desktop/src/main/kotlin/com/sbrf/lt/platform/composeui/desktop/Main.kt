package com.sbrf.lt.platform.composeui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
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
import com.sbrf.lt.platform.composeui.home.HomePageApi
import com.sbrf.lt.platform.composeui.home.HomePageData
import com.sbrf.lt.platform.composeui.home.HomePageState
import com.sbrf.lt.platform.composeui.home.HomePageStore
import com.sbrf.lt.platform.composeui.home.label
import com.sbrf.lt.platform.composeui.model.AppsRootStatus
import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogDiagnostics
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateResponse
import kotlinx.coroutines.launch

fun main() = application {
    val api = remember { DesktopHomePageApi() }
    val store = remember { HomePageStore(api = api, modeAccessError = "Desktop shell пока работает в режиме локального preview.") }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(HomePageState()) }

    LaunchedEffect(Unit) {
        state = store.startReload(state)
        state = store.load()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "DataPoolLoader Compose Desktop",
    ) {
        MaterialTheme {
            DesktopShell(
                state = state,
                onReload = {
                    scope.launch {
                        state = store.startReload(state)
                        state = store.load()
                    }
                },
                onSwitchMode = { mode ->
                    scope.launch {
                        state = store.startModeSave(state)
                        state = store.updateMode(state, mode)
                    }
                },
            )
        }
    }
}

@Composable
private fun DesktopShell(
    state: HomePageState,
    onReload: () -> Unit,
    onSwitchMode: (ModuleStoreMode) -> Unit,
) {
    val data = state.homeData
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("DataPoolLoader Desktop", style = MaterialTheme.typography.h4)
            Text(
                "Это первый каркас desktop-версии. Он уже использует shared store и shared runtime-модели, " +
                    "а browser-specific код остается в web-модуле.",
                style = MaterialTheme.typography.body1,
            )

            if (state.errorMessage != null) {
                Text("Ошибка: ${state.errorMessage}", color = MaterialTheme.colors.error)
            }

            if (state.modeAccessError != null) {
                Text("Ограничение режима: ${state.modeAccessError}", style = MaterialTheme.typography.body2)
            }

            if (data == null) {
                Text("Загрузка desktop shell...")
            } else {
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
                    OutlinedButton(
                        onClick = onReload,
                        enabled = !state.loading && !state.savingMode,
                    ) {
                        Text("Обновить")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Следующий шаг: подключить real desktop transport и вынести platform UI отдельно от web shell.")
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

private class DesktopHomePageApi : HomePageApi {
    private var currentMode: ModuleStoreMode = ModuleStoreMode.FILES

    override suspend fun loadHomePageData(): HomePageData =
        HomePageData(
            runtimeContext = RuntimeContext(
                requestedMode = currentMode,
                effectiveMode = currentMode,
                fallbackReason = "Desktop preview пока не подключен к реальному Ktor backend.",
                actor = RuntimeActorState(
                    resolved = true,
                    actorId = "desktop-user",
                    actorSource = "desktop-preview",
                    actorDisplayName = "Desktop Preview",
                    requiresManualInput = false,
                    message = "Desktop shell использует локальный preview actor.",
                ),
                database = DatabaseConnectionStatus(
                    configured = false,
                    available = false,
                    schema = "ui_registry",
                    message = "Desktop transport пока не реализован.",
                    errorMessage = null,
                ),
            ),
            filesCatalog = FilesModulesCatalogResponse(
                appsRootStatus = AppsRootStatus(
                    mode = "desktop-preview",
                    configuredPath = null,
                    message = "Desktop shell пока не читает appsRoot напрямую.",
                ),
                diagnostics = ModuleCatalogDiagnostics(totalModules = 0),
                modules = emptyList(),
            ),
            databaseCatalog = DatabaseModulesCatalogResponse(
                runtimeContext = RuntimeContext(
                    requestedMode = currentMode,
                    effectiveMode = currentMode,
                    fallbackReason = null,
                    actor = RuntimeActorState(
                        resolved = true,
                        actorId = "desktop-user",
                        actorSource = "desktop-preview",
                        actorDisplayName = "Desktop Preview",
                        requiresManualInput = false,
                        message = "Desktop shell использует локальный preview actor.",
                    ),
                    database = DatabaseConnectionStatus(
                        configured = false,
                        available = false,
                        schema = "ui_registry",
                        message = "Desktop transport пока не реализован.",
                        errorMessage = null,
                    ),
                ),
                diagnostics = ModuleCatalogDiagnostics(totalModules = 0),
                modules = emptyList(),
            ),
        )

    override suspend fun updateRuntimeMode(mode: ModuleStoreMode): RuntimeModeUpdateResponse {
        currentMode = mode
        val runtimeContext = loadHomePageData().runtimeContext
        return RuntimeModeUpdateResponse(
            message = "Desktop preview переключен в режим ${mode.label}.",
            runtimeContext = runtimeContext,
        )
    }
}

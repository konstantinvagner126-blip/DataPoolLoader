package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiRuntimeContext

internal fun UiServerContext.currentUiConfig(): UiAppConfig =
    runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig)

internal fun UiServerContext.currentRuntimeUiConfig(): UiAppConfig =
    runCatching { runtimeConfigResolver.resolve(currentUiConfig()) }.getOrDefault(runtimeUiConfig)

internal fun UiServerContext.currentRuntimeContext(): UiRuntimeContext =
    runCatching { runtimeContextService.resolve(currentRuntimeUiConfig()) }.getOrDefault(runtimeContext)

internal fun UiServerContext.resolveRuntimeContextFromConfig(config: UiAppConfig): UiRuntimeContext =
    runtimeContextService.resolve(runtimeConfigResolver.resolve(config))

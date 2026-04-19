package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.run.UiCredentialsService

internal data class UiServerStartupRuntime(
    val uiConfigLoader: UiConfigLoader,
    val credentialsService: UiCredentialsService,
    val runtimeConfigResolver: UiRuntimeConfigResolver,
    val runtimeContext: UiRuntimeContext,
)

internal fun buildUiServerStartupRuntime(
    uiConfig: UiAppConfig,
    runtimeContextService: UiRuntimeContextService,
): UiServerStartupRuntime {
    val uiConfigLoader = UiConfigLoader()
    val credentialsService = UiCredentialsService(
        uiConfigProvider = { runCatching { uiConfigLoader.load() }.getOrDefault(uiConfig) },
    )
    val runtimeConfigResolver = UiRuntimeConfigResolver(credentialsService)
    val runtimeContext = runtimeContextService.resolve(runtimeConfigResolver.resolve(uiConfig))
    return UiServerStartupRuntime(
        uiConfigLoader = uiConfigLoader,
        credentialsService = credentialsService,
        runtimeConfigResolver = runtimeConfigResolver,
        runtimeContext = runtimeContext,
    )
}

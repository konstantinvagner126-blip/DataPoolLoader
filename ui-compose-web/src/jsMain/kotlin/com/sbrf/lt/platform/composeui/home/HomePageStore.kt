package com.sbrf.lt.platform.composeui.home

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

class HomePageStore(
    private val api: HomePageApi,
    private val modeAccessError: String?,
) {
    suspend fun load(): HomePageState {
        return runCatching {
            api.loadHomePageData()
        }.fold(
            onSuccess = { data ->
                HomePageState(
                    loading = false,
                    savingMode = false,
                    errorMessage = null,
                    modeAccessError = modeAccessError,
                    homeData = data,
                )
            },
            onFailure = { error ->
                HomePageState(
                    loading = false,
                    savingMode = false,
                    errorMessage = error.message ?: "Не удалось загрузить данные главного экрана.",
                    modeAccessError = modeAccessError,
                    homeData = null,
                )
            },
        )
    }

    suspend fun updateMode(
        currentState: HomePageState,
        mode: ModuleStoreMode,
    ): HomePageState {
        return runCatching {
            api.updateRuntimeMode(mode)
            api.loadHomePageData()
        }.fold(
            onSuccess = { data ->
                currentState.copy(
                    loading = false,
                    savingMode = false,
                    errorMessage = null,
                    homeData = data,
                )
            },
            onFailure = { error ->
                currentState.copy(
                    loading = false,
                    savingMode = false,
                    errorMessage = error.message ?: "Не удалось переключить режим UI.",
                )
            },
        )
    }

    fun startReload(currentState: HomePageState): HomePageState {
        return currentState.copy(loading = true, errorMessage = null)
    }

    fun startModeSave(currentState: HomePageState): HomePageState {
        return currentState.copy(savingMode = true, errorMessage = null)
    }
}

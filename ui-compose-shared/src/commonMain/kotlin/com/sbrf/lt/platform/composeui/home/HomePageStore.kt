package com.sbrf.lt.platform.composeui.home

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateResponse

interface HomePageApi {
    suspend fun loadHomePageData(): HomePageData

    suspend fun updateRuntimeMode(mode: ModuleStoreMode): RuntimeModeUpdateResponse
}

class HomePageStore(
    private val api: HomePageApi,
    private val modeAccessError: String?,
) {
    private val support = HomePageStoreSupport(api, modeAccessError)

    suspend fun load(): HomePageState =
        support.load()

    suspend fun updateMode(
        currentState: HomePageState,
        mode: ModuleStoreMode,
    ): HomePageState =
        support.updateMode(currentState, mode)

    fun startReload(currentState: HomePageState): HomePageState {
        return currentState.copy(loading = true, errorMessage = null)
    }

    fun startModeSave(currentState: HomePageState): HomePageState {
        return currentState.copy(savingMode = true, errorMessage = null)
    }
}

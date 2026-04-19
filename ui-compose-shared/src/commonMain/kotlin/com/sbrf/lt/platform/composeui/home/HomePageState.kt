package com.sbrf.lt.platform.composeui.home

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.RuntimeContext

data class HomePageData(
    val runtimeContext: RuntimeContext,
    val filesCatalog: FilesModulesCatalogResponse,
    val databaseCatalog: DatabaseModulesCatalogResponse? = null,
)

data class HomePageState(
    val loading: Boolean = true,
    val savingMode: Boolean = false,
    val errorMessage: String? = null,
    val modeAccessError: String? = null,
    val homeData: HomePageData? = null,
)

package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.browser.window

class ModuleEditorStore(
    private val api: ModuleEditorApi,
) {
    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
        return runCatching {
            if (route.storage == "database") {
                val catalog = api.loadDatabaseCatalog(route.includeHidden)
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadDatabaseSession(moduleId) }
                syncBrowserUrl(route.storage, selectedModuleId, route.includeHidden)
                ModuleEditorPageState(
                    loading = false,
                    errorMessage = null,
                    databaseCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                    session = session,
                    selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
                )
            } else {
                val catalog = api.loadFilesCatalog()
                val selectedModuleId = resolveSelectedModuleId(route.moduleId, catalog.modules.map { it.id })
                val session = selectedModuleId?.let { moduleId -> api.loadFilesSession(moduleId) }
                syncBrowserUrl(route.storage, selectedModuleId, route.includeHidden)
                ModuleEditorPageState(
                    loading = false,
                    errorMessage = null,
                    filesCatalog = catalog,
                    selectedModuleId = selectedModuleId,
                    session = session,
                    selectedSqlPath = session?.module?.sqlFiles?.firstOrNull()?.path,
                )
            }
        }.getOrElse { error ->
            ModuleEditorPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить редактор модуля.",
            )
        }
    }

    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState {
        return runCatching {
            val session = if (route.storage == "database") {
                api.loadDatabaseSession(moduleId)
            } else {
                api.loadFilesSession(moduleId)
            }
            syncBrowserUrl(route.storage, moduleId, route.includeHidden)
            current.copy(
                loading = false,
                errorMessage = null,
                selectedModuleId = moduleId,
                session = session,
                selectedSqlPath = session.module.sqlFiles.firstOrNull()?.path,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить выбранный модуль.",
            )
        }
    }

    fun selectTab(
        current: ModuleEditorPageState,
        tab: ModuleEditorTab,
    ): ModuleEditorPageState =
        current.copy(activeTab = tab)

    fun selectSqlResource(
        current: ModuleEditorPageState,
        path: String,
    ): ModuleEditorPageState =
        current.copy(selectedSqlPath = path)

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(loading = true, errorMessage = null)

    private fun resolveSelectedModuleId(
        preferredId: String?,
        moduleIds: List<String>,
    ): String? =
        when {
            preferredId != null && moduleIds.contains(preferredId) -> preferredId
            else -> moduleIds.firstOrNull()
        }

    private fun syncBrowserUrl(
        storage: String,
        moduleId: String?,
        includeHidden: Boolean,
    ) {
        val query = buildString {
            append("?storage=")
            append(storage)
            if (!moduleId.isNullOrBlank()) {
                append("&module=")
                append(moduleId)
            }
            if (includeHidden) {
                append("&includeHidden=true")
            }
        }
        window.history.replaceState(null, "", "/compose-editor$query")
    }
}

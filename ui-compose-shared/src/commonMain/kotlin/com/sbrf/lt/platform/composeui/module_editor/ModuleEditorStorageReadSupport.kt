package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse

internal data class ModuleEditorCatalogSnapshot(
    val selectedModuleId: String?,
    val session: ModuleEditorSessionResponse?,
    val configForm: ConfigFormSnapshot?,
    val filesCatalog: FilesModulesCatalogResponse? = null,
    val databaseCatalog: DatabaseModulesCatalogResponse? = null,
)

internal data class ModuleEditorCatalogRefreshSnapshot(
    val selectedModuleId: String?,
    val filesCatalog: FilesModulesCatalogResponse? = null,
    val databaseCatalog: DatabaseModulesCatalogResponse? = null,
)

internal data class ModuleEditorSessionSnapshot(
    val moduleId: String,
    val session: ModuleEditorSessionResponse,
    val configForm: ConfigFormSnapshot,
)

internal class ModuleEditorStorageReadSupport(
    private val api: ModuleEditorApi,
    private val selectionSupport: ModuleEditorStoreCatalogSelectionSupport,
    private val configFormSnapshotStore: ModuleEditorConfigFormSnapshotStore,
) {
    suspend fun loadCatalogSnapshot(route: ModuleEditorRouteState): ModuleEditorCatalogSnapshot =
        if (route.storage == "database") {
            val catalog = api.loadDatabaseCatalog(route.includeHidden)
            val selectedModuleId = selectionSupport.resolveInitialSelectedModuleId(
                route.moduleId,
                catalog.modules.map { it.id },
            )
            val session = selectedModuleId?.let { moduleId -> api.loadDatabaseSession(moduleId) }
            val configForm = session?.let { configFormSnapshotStore.loadSnapshot(it.module.configText) }
            ModuleEditorCatalogSnapshot(
                selectedModuleId = selectedModuleId,
                session = session,
                configForm = configForm,
                databaseCatalog = catalog,
            )
        } else {
            val catalog = api.loadFilesCatalog()
            val selectedModuleId = selectionSupport.resolveInitialSelectedModuleId(
                route.moduleId,
                catalog.modules.map { it.id },
            )
            val session = selectedModuleId?.let { moduleId -> api.loadFilesSession(moduleId) }
            val configForm = session?.let { configFormSnapshotStore.loadSnapshot(it.module.configText) }
            ModuleEditorCatalogSnapshot(
                selectedModuleId = selectedModuleId,
                session = session,
                configForm = configForm,
                filesCatalog = catalog,
            )
        }

    suspend fun refreshCatalogSnapshot(
        route: ModuleEditorRouteState,
        currentSelectedModuleId: String?,
    ): ModuleEditorCatalogRefreshSnapshot =
        if (route.storage == "database") {
            val catalog = api.loadDatabaseCatalog(route.includeHidden)
            ModuleEditorCatalogRefreshSnapshot(
                selectedModuleId = selectionSupport.resolveRefreshedSelectedModuleId(
                    currentSelectedModuleId,
                    catalog.modules.map { it.id },
                ),
                databaseCatalog = catalog,
            )
        } else {
            val catalog = api.loadFilesCatalog()
            ModuleEditorCatalogRefreshSnapshot(
                selectedModuleId = selectionSupport.resolveRefreshedSelectedModuleId(
                    currentSelectedModuleId,
                    catalog.modules.map { it.id },
                ),
                filesCatalog = catalog,
            )
        }

    suspend fun loadSessionSnapshot(
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorSessionSnapshot {
        val session = if (route.storage == "database") {
            api.loadDatabaseSession(moduleId)
        } else {
            api.loadFilesSession(moduleId)
        }
        val configForm = configFormSnapshotStore.loadSnapshot(session.module.configText)
        return ModuleEditorSessionSnapshot(
            moduleId = moduleId,
            session = session,
            configForm = configForm,
        )
    }
}
